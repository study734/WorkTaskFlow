package com.teamproject.payment.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String secretKey;

    public TossPaymentsClient(ObjectMapper mapper,
            @Value("${app.toss.api-base:https://api.tosspayments.com}") String apiBase,
            @Value("${app.toss.secret-key:}") String secretKey) {
        this.mapper = mapper;
        this.secretKey = secretKey;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(65));
        this.client = RestClient.builder().baseUrl(apiBase).requestFactory(requestFactory).build();
    }

    public boolean configured() { return secretKey != null && !secretKey.isBlank(); }
    public boolean usesOfficialTestKey() {
        return secretKey != null && (secretKey.startsWith("test_sk") || secretKey.startsWith("test_gsk"));
    }

    public ApiResult issueBillingKey(String authKey, String customerKey, String idempotencyKey) {
        return post("/v1/billing/authorizations/issue", Map.of("authKey", authKey, "customerKey", customerKey),
                idempotencyKey);
    }

    public ApiResult testCharge(String billingKey, String customerKey, long amount, String orderId,
            String idempotencyKey) {
        return charge(billingKey, customerKey, amount, orderId, "퇴사 연동 테스트", idempotencyKey);
    }

    public ApiResult charge(String billingKey, String customerKey, long amount, String orderId,
            String orderName, String idempotencyKey) {
        return post("/v1/billing/" + billingKey, Map.of(
                "customerKey", customerKey, "amount", amount, "orderId", orderId,
                "orderName", orderName), idempotencyKey);
    }

    private ApiResult post(String path, Object body, String idempotencyKey) {
        try {
            String response = client.post().uri(path)
                    .header("Authorization", basicAuthorization())
                    .header("Idempotency-Key", idempotencyKey)
                    .header("Accept-Language", "ko")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(String.class);
            return new ApiResult(200, parse(response), null, null);
        } catch (RestClientResponseException exception) {
            JsonNode error = parse(exception.getResponseBodyAsString());
            JsonNode nested = error.path("error").isObject() ? error.path("error") : error;
            return new ApiResult(exception.getStatusCode().value(), error,
                    text(nested, "code", "TOSS_API_ERROR"), text(nested, "message", "토스페이먼츠 요청에 실패했습니다."));
        } catch (RuntimeException exception) {
            return new ApiResult(null, mapper.createObjectNode(), "TOSS_NETWORK_ERROR",
                    "토스페이먼츠 서버와 통신하지 못했습니다.");
        }
    }

    private String basicAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
    private JsonNode parse(String value) {
        try { return value == null || value.isBlank() ? mapper.createObjectNode() : mapper.readTree(value); }
        catch (Exception exception) { return mapper.createObjectNode(); }
    }
    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
    public record ApiResult(Integer status, JsonNode body, String errorCode, String errorMessage) {
        public boolean successful() { return status != null && status >= 200 && status < 300; }
    }
}
