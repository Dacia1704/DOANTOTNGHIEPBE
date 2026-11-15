package com.tamdaptit.rentalmanagement.moderation.service;

import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.*;
import java.util.List;

public interface ContentModerationService {
    Evaluation evaluate(String scope,String text);
    void record(String type,String id,String author,long version,String text,Evaluation result);
    List<Rule> rules(String actor);
    Rule saveRule(String actor,String id,RuleInput input);
    Evaluation test(String actor,String scope,String text);
    List<ModerationCase> cases(String actor,boolean resolved,int page);
    void decide(String actor,String caseId,DecisionInput input);
}
