package com.tamdaptit.rentalmanagement.moderation.service;

import java.util.List;

import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.DecisionInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Evaluation;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ModerationCase;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Rule;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.RuleInput;

public interface ContentModerationService {
    Evaluation evaluate(String scope, String text);

    void record(
        String type,
        String id,
        String author,
        long version,
        String text,
        Evaluation result
    );

    List<Rule> rules(String actor);

    Rule saveRule(
        String actor,
        String id,
        RuleInput input
    );

    Evaluation test(
        String actor,
        String scope,
        String text
    );

    List<ModerationCase> cases(
        String actor,
        boolean resolved,
        int page
    );

    void decide(
        String actor,
        String caseId,
        DecisionInput input
    );
}
