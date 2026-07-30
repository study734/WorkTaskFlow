package com.teamproject.subscription.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class SubscriptionDtos {
    private SubscriptionDtos() {}
    public record SubscriptionResponse(Long id, Long groupId, String planCode, String status,
            long amount, String currency, String conversionChoice, LocalDateTime rolloutNoticeAt,
            LocalDateTime decisionDeadline, LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd, LocalDateTime nextBillingAt,
            boolean liveBillingEnabled, boolean canStartTrial) {}
    public record ConversionChoiceRequest(@NotBlank String choice) {}
    public record ActivateSubscriptionRequest(@NotNull Long paymentMethodId,
            @AssertTrue boolean recurringBillingConsent,
            @AssertTrue boolean policyConsent,
            @NotBlank @Size(max = 30) String termsVersion,
            @NotBlank @Size(max = 30) String refundPolicyVersion) {}
}
