package com.tamdaptit.rentalmanagement.moderation.service.impl;

import com.tamdaptit.rentalmanagement.community.CommunityAccess;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.*;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.moderation.ContentMatcher;
import com.tamdaptit.rentalmanagement.moderation.service.ContentModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.*;
import java.util.*;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

/** JDBC owner: moderation_rules, content_moderation_cases, moderation_actions. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ContentModerationServiceImpl implements ContentModerationService {
    private final JdbcTemplate jdbc;
    private final CommunityAccess access;
    private final Clock clock;
    private final com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher notifications;
    private Rule rule(ResultSet r,int row) throws SQLException {
        return new Rule(r.getString("id"),r.getString("pattern"),r.getString("match_type"),r.getString("action"),r.getBoolean("accent_insensitive"),
            r.getBoolean("apply_posts"),r.getBoolean("apply_comments"),r.getBoolean("apply_reviews"),r.getBoolean("apply_replies"),r.getBoolean("enabled"),r.getString("reason"),r.getLong("version"));
    }
    @Override public Evaluation evaluate(String scope,String text) {
        int rank=0; var reasons=new LinkedHashSet<String>();
        for(var r:jdbc.query("SELECT * FROM moderation_rules WHERE enabled=true ORDER BY id",this::rule)) {
            boolean applies=switch(scope) { case "POST" -> r.applyPosts(); case "POST_COMMENT" -> r.applyComments();
                case "ROOM_REVIEW_TEXT","LANDLORD_REVIEW_TEXT" -> r.applyReviews();
                case "ROOM_REVIEW_REPLY","LANDLORD_REVIEW_REPLY" -> r.applyReplies(); default -> false; };
            if(applies && ContentMatcher.matches(r,text)) {
                rank=Math.max(rank,switch(r.action()){case "HIDE"->3;case "REQUIRE_REVIEW"->2;default->1;});
                reasons.add(r.reason()+" ["+r.id()+" v"+r.version()+"]");
            }
        }
        String reason=String.join("; ",reasons);
        return new Evaluation(switch(rank){case 3->"HIDDEN";case 2->"PENDING";case 1->"RESTRICTED";default->"VISIBLE";}, reason.length()>1000?reason.substring(0,1000):reason);
    }
    @Override @Transactional public void record(String type,String id,String author,long version,String text,Evaluation result) {
        if("VISIBLE".equals(result.status())) return;
        jdbc.update("INSERT INTO content_moderation_cases(id,target_type,target_id,author_id,content_version,snapshot,reason,status,source,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),type,id,author,version,text==null?"":text,result.reason().length()>1000?result.reason().substring(0,1000):result.reason(),result.status(),"AUTO_RULE",LocalDateTime.now(clock));
    }
    @Override public List<Rule> rules(String actor) {access.permission(actor,"MODERATION_READ");return jdbc.query("SELECT * FROM moderation_rules ORDER BY pattern",this::rule);}
    @Override @Transactional public Rule saveRule(String actor,String id,RuleInput b) {
        access.permission(actor,"MODERATION_RULE_MANAGE");
        if(!(b.applyPosts()||b.applyComments()||b.applyReviews()||b.applyReplies()) || ContentMatcher.normalize(b.pattern(),false).isBlank()) throw new AppException(INVALID_REQUEST);
        if("TOKEN".equals(b.matchType()) && ContentMatcher.normalize(b.pattern(),false).contains(" ")) throw new AppException(INVALID_REQUEST);
        if(id==null) {
            id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO moderation_rules(id,pattern,match_type,action,accent_insensitive,apply_posts,apply_comments,apply_reviews,apply_replies,enabled,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id,b.pattern().strip(),b.matchType(),b.action(),b.accentInsensitive(),b.applyPosts(),b.applyComments(),b.applyReviews(),b.applyReplies(),b.enabled(),b.reason());
        } else if(jdbc.update("UPDATE moderation_rules SET pattern=?,match_type=?,action=?,accent_insensitive=?,apply_posts=?,apply_comments=?,apply_reviews=?,apply_replies=?,enabled=?,reason=?,version=version+1 WHERE id=? AND version=?",
            b.pattern().strip(),b.matchType(),b.action(),b.accentInsensitive(),b.applyPosts(),b.applyComments(),b.applyReviews(),b.applyReplies(),b.enabled(),b.reason(),id,b.version())!=1) throw new AppException(CONTENT_VERSION_CONFLICT);
        audit(actor,id,"RULE_SAVED",b.reason());
        return jdbc.queryForObject("SELECT * FROM moderation_rules WHERE id=?",this::rule,id);
    }
    @Override public Evaluation test(String actor,String scope,String text) {access.permission(actor,"MODERATION_RULE_MANAGE");if(text==null||text.length()>10000)throw new AppException(INVALID_REQUEST);return evaluate(scope,text);}
    @Override public List<ModerationCase> cases(String actor,boolean resolved,int page) {
        access.permission(actor,"MODERATION_READ");if(page<0||page>10000)throw new AppException(INVALID_REQUEST);
        String scope=access.hasPermission(actor,"REVIEW_READ")?"":" AND c.target_type IN ('POST','POST_COMMENT')";
        return jdbc.query("SELECT c.*,u.full_name FROM content_moderation_cases c JOIN users u ON u.id=c.author_id WHERE c.resolved=?"+scope+" ORDER BY c.created_at DESC,c.id LIMIT 50 OFFSET ?",
            (r,n)->new ModerationCase(r.getString("id"),r.getString("target_type"),r.getString("target_id"),r.getString("full_name"),r.getString("snapshot"),r.getString("reason"),r.getString("status"),r.getString("source"),r.getBoolean("resolved"),r.getLong("content_version"),r.getTimestamp("created_at").toLocalDateTime()),resolved,page*50);
    }
    @Override @Transactional public void decide(String actor,String caseId,DecisionInput b) {
        access.permission(actor,"MODERATION_HANDLE");
        var rows=jdbc.queryForList("SELECT * FROM content_moderation_cases WHERE id=?",caseId);
        if(rows.isEmpty())throw new AppException(NOT_FOUND);
        var c=rows.getFirst();String type=(String)c.get("target_type"),target=(String)c.get("target_id");
        if(type.contains("REVIEW"))access.permission(actor,"REVIEW_MODERATE");
        String table, statusColumn, textColumn;
        switch(type) {
            case "POST" -> {table="community_posts";statusColumn="status";textColumn="content";}
            case "POST_COMMENT" -> {table="community_comments";statusColumn="status";textColumn="content";}
            case "ROOM_REVIEW_TEXT","ROOM_REVIEW_REPLY" -> {table="room_reviews";statusColumn=type.endsWith("REPLY")?"reply_status":"text_status";textColumn=type.endsWith("REPLY")?"landlord_reply":"comment";}
            case "LANDLORD_REVIEW_TEXT","LANDLORD_REVIEW_REPLY" -> {table="landlord_reviews";statusColumn=type.endsWith("REPLY")?"reply_status":"text_status";textColumn=type.endsWith("REPLY")?"landlord_reply":"comment";}
            default -> throw new AppException(INVALID_REQUEST);
        }
        // Target first, then case: same lock order as content/report mutations.
        var current=jdbc.queryForMap("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",target);
        c=jdbc.queryForMap("SELECT * FROM content_moderation_cases WHERE id=? FOR UPDATE",caseId);
        if(Boolean.TRUE.equals(c.get("resolved")) || ((Number)c.get("content_version")).longValue()!=b.expectedVersion()
            || !Objects.equals(current.get(textColumn),c.get("snapshot"))) throw new AppException(CONTENT_VERSION_CONFLICT);
        if(current.containsKey("version") && ((Number)current.get("version")).longValue()!=b.expectedVersion())throw new AppException(CONTENT_VERSION_CONFLICT);
        jdbc.update("UPDATE "+table+" SET "+statusColumn+"=? WHERE id=?",b.status(),target);
        jdbc.update("UPDATE content_moderation_cases SET resolved=true,status=?,handled_by=?,resolution_note=? WHERE target_type=? AND target_id=? AND resolved=false",b.status(),actor,b.reason(),type,target);
        audit(actor,target,"CONTENT_"+b.status(),b.reason());
        String postId="POST".equals(type)?target:"POST_COMMENT".equals(type)?(String)current.get("post_id"):null;
        notifications.enqueue((String)c.get("author_id"),com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType.SYSTEM,"Kết quả kiểm duyệt nội dung",b.reason(),postId==null?null:"COMMUNITY_POST",postId);
    }
    private void audit(String actor,String target,String action,String reason) {
        jdbc.update("INSERT INTO moderation_actions(id,actor_id,target_id,action,reason,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,target,action,reason,LocalDateTime.now(clock));
    }
}
