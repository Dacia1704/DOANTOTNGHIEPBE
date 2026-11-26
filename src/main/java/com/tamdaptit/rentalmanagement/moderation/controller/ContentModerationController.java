package com.tamdaptit.rentalmanagement.moderation.controller;

import java.security.Principal;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.DecisionInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Evaluation;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ModerationCase;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Rule;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.RuleInput;
import com.tamdaptit.rentalmanagement.moderation.service.ContentModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/moderation")
@RequiredArgsConstructor
public class ContentModerationController {
    private final ContentModerationService service;

    @GetMapping("/rules")
    public ApiResponse<List<Rule>> rules(Principal p) {
        return ApiResponse.success(service.rules(p.getName()));
    }

    @PostMapping("/rules")
    public ApiResponse<Rule> create(Principal p, @Valid @RequestBody RuleInput b) {
        return ApiResponse.success(service.saveRule(p.getName(), null, b));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Rule> update(
        Principal p,
        @PathVariable String id,
        @Valid @RequestBody RuleInput b
    ) {
        return ApiResponse.success(service.saveRule(p.getName(), id, b));
    }

    public record TestInput(String scope, String text) {}

    @PostMapping("/rules/test")
    public ApiResponse<Evaluation> test(Principal p, @RequestBody TestInput b) {
        return ApiResponse.success(service.test(p.getName(), b.scope(), b.text()));
    }

    @GetMapping("/cases")
    public ApiResponse<List<ModerationCase>> cases(
        Principal p,
        @RequestParam(defaultValue = "false") boolean resolved,
        @RequestParam(defaultValue = "0") int page
    ) {
        return ApiResponse.success(service.cases(p.getName(), resolved, page));
    }

    @PostMapping("/cases/{id}/decisions")
    public ApiResponse<Void> decide(
        Principal p,
        @PathVariable String id,
        @Valid @RequestBody DecisionInput b
    ) {
        service.decide(p.getName(), id, b);
        return ApiResponse.success(null);
    }
}
