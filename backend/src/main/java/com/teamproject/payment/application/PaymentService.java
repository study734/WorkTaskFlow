package com.teamproject.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.payment.application.dto.PaymentDtos.*;
import com.teamproject.payment.domain.*;
import com.teamproject.payment.infrastructure.PaymentSecretCipher;
import com.teamproject.payment.infrastructure.TossPaymentsClient;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    private static final Logger audit = LoggerFactory.getLogger("PAYMENT_AUDIT");
    private final UserRepository users;
    private final PaymentMethodRepository methods;
    private final PaymentAttemptRepository attempts;
    private final TossPaymentsClient toss;
    private final PaymentSecretCipher cipher;
    private final String clientKey;
    private final boolean testMode;

    public PaymentService(UserRepository users, PaymentMethodRepository methods, PaymentAttemptRepository attempts,
            TossPaymentsClient toss, PaymentSecretCipher cipher,
            @Value("${app.toss.client-key:}") String clientKey,
            @Value("${app.toss.test-mode:true}") boolean testMode) {
        this.users = users; this.methods = methods; this.attempts = attempts; this.toss = toss; this.cipher = cipher;
        this.clientKey = clientKey; this.testMode = testMode;
    }

    @Transactional
    public PaymentConfigResponse config(Long userId) {
        User user = user(userId);
        user.ensurePaymentCustomerKey("cust-" + UUID.randomUUID());
        boolean configured = toss.configured() && cipher.configured() && !clientKey.isBlank();
        return new PaymentConfigResponse(configured, testMode && toss.usesOfficialTestKey(), configured ? clientKey : null,
                configured ? user.getPaymentCustomerKey() : null);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> methods(Long userId) {
        user(userId);
        return methods.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::response).toList();
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public PaymentMethodResponse issue(Long userId, IssuePaymentMethodRequest request) {
        requireConfigured();
        User user = user(userId);
        if (!request.customerKey().equals(user.getPaymentCustomerKey())) {
            throw new ApplicationException("PAYMENT_CUSTOMER_MISMATCH", HttpStatus.BAD_REQUEST,
                    "결제 고객 정보가 일치하지 않습니다.");
        }
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(user, null,
                PaymentAttempt.OperationType.BILLING_KEY_ISSUE, idempotencyKey, null, null));
        TossPaymentsClient.ApiResult result = toss.issueBillingKey(request.authKey(), request.customerKey(), idempotencyKey);
        if (!result.successful()) {
            fail(attempt, result);
            throw providerFailure(result);
        }
        JsonNode body = result.body();
        String billingKey = body.path("billingKey").asText("");
        if (billingKey.isBlank()) {
            attempt.fail(result.status(), "INVALID_PROVIDER_RESPONSE", "빌링키가 없는 응답입니다.");
            throw new ApplicationException("PAYMENT_PROVIDER_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY,
                    "결제수단 등록 응답을 확인하지 못했습니다.");
        }
        methods.findAllByUserIdAndStatus(userId, PaymentMethod.Status.ACTIVE).forEach(PaymentMethod::deactivate);
        JsonNode card = body.path("card");
        PaymentMethod method = methods.save(new PaymentMethod(user, cipher.encrypt(billingKey),
                value(card, "issuerCode"), value(card, "number")));
        attempt.attachMethod(method);
        attempt.success(result.status());
        audit.info("event=PAYMENT_METHOD_ISSUED outcome=SUCCESS userId={} methodId={} attemptId={}",
                userId, method.getId(), attempt.getId());
        return response(method);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public PaymentAttemptResponse testCharge(Long userId, Long methodId, TestChargeRequest request) {
        requireTestMode();
        PaymentMethod method = activeMethod(userId, methodId);
        User user = user(userId);
        String orderId = "test-" + UUID.randomUUID();
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(user, method,
                PaymentAttempt.OperationType.TEST_CHARGE, UUID.randomUUID().toString(), orderId, request.amount()));
        executeCharge(attempt);
        return response(attempt);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public PaymentAttemptResponse retry(Long userId, Long attemptId) {
        requireTestMode();
        PaymentAttempt attempt = attempts.findByIdAndUserId(attemptId, userId).orElseThrow(this::attemptNotFound);
        if (attempt.getOperationType() != PaymentAttempt.OperationType.TEST_CHARGE
                || attempt.getStatus() != PaymentAttempt.Status.FAILED || attempt.getRetryCount() >= 3) {
            throw new ApplicationException("PAYMENT_RETRY_NOT_ALLOWED", HttpStatus.CONFLICT,
                    "이 요청은 재전송할 수 없습니다.");
        }
        attempt.retrying();
        executeCharge(attempt);
        return response(attempt);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public PaymentAttemptResponse subscriptionCharge(Long userId, Long methodId, Long groupId, long amount) {
        requireConfigured();
        if (amount < 100) {
            throw new ApplicationException("SUBSCRIPTION_AMOUNT_INVALID", HttpStatus.BAD_REQUEST, "구독 결제 금액을 확인해 주세요.");
        }
        PaymentMethod method = activeMethod(userId, methodId);
        User user = user(userId);
        String orderId = "sub-" + groupId + "-" + UUID.randomUUID();
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(user, method,
                PaymentAttempt.OperationType.SUBSCRIPTION_CHARGE, UUID.randomUUID().toString(), orderId, amount));
        TossPaymentsClient.ApiResult result = toss.charge(cipher.decrypt(method.getEncryptedBillingKey()),
                user.getPaymentCustomerKey(), amount, orderId, "퇴사 팀 구독", attempt.getIdempotencyKey());
        if (!result.successful()) {
            fail(attempt, result);
            throw providerFailure(result);
        }
        attempt.success(result.status());
        audit.info("event=SUBSCRIPTION_CHARGE outcome=SUCCESS userId={} groupId={} methodId={} attemptId={} amount={}",
                userId, groupId, methodId, attempt.getId(), amount);
        return response(attempt);
    }

    @Transactional(readOnly = true)
    public List<PaymentAttemptResponse> attempts(Long userId) {
        user(userId);
        return attempts.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::response).toList();
    }

    private void executeCharge(PaymentAttempt attempt) {
        PaymentMethod method = attempt.getMethod();
        TossPaymentsClient.ApiResult result = toss.testCharge(cipher.decrypt(method.getEncryptedBillingKey()),
                attempt.getUser().getPaymentCustomerKey(), attempt.getAmount(), attempt.getOrderId(),
                attempt.getIdempotencyKey());
        if (!result.successful()) {
            fail(attempt, result);
            throw providerFailure(result);
        }
        attempt.success(result.status());
        audit.info("event=PAYMENT_TEST_CHARGE outcome=SUCCESS userId={} methodId={} attemptId={} amount={}",
                attempt.getUser().getId(), method.getId(), attempt.getId(), attempt.getAmount());
    }

    private void fail(PaymentAttempt attempt, TossPaymentsClient.ApiResult result) {
        attempt.fail(result.status(), result.errorCode(), result.errorMessage());
        audit.warn("event=PAYMENT_API_CALL outcome=FAILED userId={} attemptId={} operation={} httpStatus={} providerCode={}",
                attempt.getUser().getId(), attempt.getId(), attempt.getOperationType(), result.status(), result.errorCode());
    }
    private PaymentMethod activeMethod(Long userId, Long id) {
        PaymentMethod method = methods.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApplicationException("PAYMENT_METHOD_NOT_FOUND", HttpStatus.NOT_FOUND, "결제수단을 찾을 수 없습니다."));
        if (method.getStatus() != PaymentMethod.Status.ACTIVE) {
            throw new ApplicationException("PAYMENT_METHOD_INACTIVE", HttpStatus.CONFLICT, "사용할 수 없는 결제수단입니다.");
        }
        return method;
    }
    private User user(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
    private void requireConfigured() {
        if (!toss.configured() || !cipher.configured() || clientKey.isBlank()) {
            throw new ApplicationException("PAYMENT_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "결제 연동 환경변수가 설정되지 않았습니다.");
        }
    }
    private void requireTestMode() {
        requireConfigured();
        if (!testMode || !toss.usesOfficialTestKey()) {
            throw new ApplicationException("PAYMENT_TEST_DISABLED", HttpStatus.FORBIDDEN,
                    "테스트 결제는 test_sk 또는 test_gsk 키를 설정한 테스트 환경에서만 실행할 수 있습니다.");
        }
    }
    private ApplicationException providerFailure(TossPaymentsClient.ApiResult result) {
        HttpStatus status = result.status() == null ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_GATEWAY;
        return new ApplicationException(result.errorCode(), status, result.errorMessage());
    }
    private ApplicationException attemptNotFound() {
        return new ApplicationException("PAYMENT_ATTEMPT_NOT_FOUND", HttpStatus.NOT_FOUND, "결제 호출 기록을 찾을 수 없습니다.");
    }
    private String value(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }
    private PaymentMethodResponse response(PaymentMethod method) {
        return new PaymentMethodResponse(method.getId(), method.getProvider(), method.getIssuerCode(),
                method.getMaskedNumber(), method.getStatus().name(), method.getCreatedAt());
    }
    private PaymentAttemptResponse response(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(attempt.getId(),
                attempt.getMethod() == null ? null : attempt.getMethod().getId(), attempt.getOperationType().name(),
                attempt.getOrderId(), attempt.getAmount(), attempt.getStatus().name(), attempt.getHttpStatus(),
                attempt.getProviderCode(), attempt.getProviderMessage(), attempt.getRetryCount(), attempt.getCreatedAt());
    }
}
