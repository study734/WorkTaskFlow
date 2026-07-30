package com.teamproject.subscription.presentation;

import com.teamproject.subscription.application.SubscriptionService;
import com.teamproject.subscription.application.dto.SubscriptionDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/subscription")
public class SubscriptionController {
    private final SubscriptionService subscriptions;
    public SubscriptionController(SubscriptionService subscriptions) { this.subscriptions = subscriptions; }
    @GetMapping SubscriptionResponse get(Authentication auth, @PathVariable Long groupId) {
        return subscriptions.get((Long) auth.getPrincipal(), groupId);
    }
    @PostMapping("/trial") SubscriptionResponse trial(Authentication auth, @PathVariable Long groupId) {
        return subscriptions.startTrial((Long) auth.getPrincipal(), groupId);
    }
    @PutMapping("/conversion-choice")
    SubscriptionResponse choose(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody ConversionChoiceRequest request) {
        return subscriptions.choose((Long) auth.getPrincipal(), groupId, request.choice());
    }
    @PostMapping("/activate")
    SubscriptionResponse activate(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody ActivateSubscriptionRequest request, HttpServletRequest servletRequest) {
        return subscriptions.activate((Long) auth.getPrincipal(), groupId, request,
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    }
    @PostMapping("/cancel") SubscriptionResponse cancel(Authentication auth, @PathVariable Long groupId) {
        return subscriptions.cancel((Long) auth.getPrincipal(), groupId);
    }
}
