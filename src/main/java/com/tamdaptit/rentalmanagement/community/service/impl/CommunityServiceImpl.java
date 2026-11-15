package com.tamdaptit.rentalmanagement.community.service.impl;

import com.tamdaptit.rentalmanagement.community.CommunityAccess;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.*;
import com.tamdaptit.rentalmanagement.community.service.CommunityService;
import com.tamdaptit.rentalmanagement.moderation.service.ContentModerationService;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.storage.service.ImageStorageService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

/** JDBC owner for community_posts/media/post_listings/comments/reactions/saved_posts/follows and content_reports. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CommunityServiceImpl implements CommunityService {
    private final JdbcTemplate jdbc;
    private final CommunityAccess access;
    private final ContentModerationService moderation;
    private final ImageStorageService images;
    private final ListingRepository listings;
    private final Clock clock;
    private final com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher notifications;
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    @Override public List<ListingLink> listingChoices(String query) {
        return listings.findForCommunity(now(),content(query,100,false),org.springframework.data.domain.PageRequest.of(0,20)).stream().map(l->new ListingLink(l.getId(),l.getTitle(),true)).toList();
    }
    private boolean count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args)>0;}
    private static boolean visible(String status){return Set.of("VISIBLE","RESTRICTED").contains(status);}
    private String content(String value,int max,boolean required) {
        String s=value==null?"":value.strip();
        if(s.codePointCount(0,s.length())>max || (required&&s.isBlank()))throw new AppException(INVALID_REQUEST);
        return s;
    }
    private Map<String,Object> row(String table,String id,boolean lock) {
        var rows=jdbc.queryForList("SELECT t.*,u.status author_status FROM "+table+" t JOIN users u ON u.id=t.author_id WHERE t.id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw new AppException(NOT_FOUND);return rows.getFirst();
    }
    private void readable(String actor,Map<String,Object> r) {
        boolean own=Objects.equals(actor,r.get("author_id"));
        if(r.get("deleted_at")!=null || (!own && (!visible((String)r.get("status")) || !"ACTIVE".equals(r.get("author_status")))))throw new AppException(NOT_FOUND);
    }
    private Map<String,Object> publicPost(String actor,String id,boolean lock) {
        var p=row("community_posts",id,lock);readable(actor,p);return p;
    }
    private void interact(Map<String,Object> r) {if(!"VISIBLE".equals(r.get("status")) || r.get("deleted_at")!=null || !"ACTIVE".equals(r.get("author_status")))throw new AppException(NOT_FOUND);}
    private Map<String,Object> target(String actor,String type,String id,boolean lock) {
        if("POST".equals(type))return publicPost(actor,id,lock);
        if(!"POST_COMMENT".equals(type))throw new AppException(INVALID_REQUEST);
        var c=row("community_comments",id,lock);publicPost(actor,(String)c.get("post_id"),false);readable(actor,c);
        if(c.get("parent_id")!=null) {
            var parent=row("community_comments",(String)c.get("parent_id"),false);
            if(!visible((String)parent.get("status")) || !"ACTIVE".equals(parent.get("author_status")))throw new AppException(NOT_FOUND);
        }
        return c;
    }
    private record Cursor(LocalDateTime at,String id) {}
    private Cursor decode(String cursor) {
        if(cursor==null||cursor.isBlank())return null;
        try {String[] p=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",2);return new Cursor(LocalDateTime.parse(p[0]),UUID.fromString(p[1]).toString());}
        catch(RuntimeException ex){throw new AppException(INVALID_REQUEST);}
    }
    private String encode(LocalDateTime at,String id){return Base64.getUrlEncoder().withoutPadding().encodeToString((at+"|"+id).getBytes(StandardCharsets.UTF_8));}
    @Override public Slice<Post> feed(String actor,String tab,String cursor,String q,String area) {
        if(!Set.of("LATEST","FOLLOWING","SAVED","MINE","NEARBY").contains(tab))throw new AppException(INVALID_REQUEST);
        if(Set.of("FOLLOWING","SAVED","MINE").contains(tab))access.active(actor);
        var args=new ArrayList<Object>();String sql="SELECT p.id FROM community_posts p JOIN users u ON u.id=p.author_id WHERE p.deleted_at IS NULL AND u.status='ACTIVE'";
        if("MINE".equals(tab)){sql+=" AND p.author_id=?";args.add(actor);}else sql+=" AND p.status IN ('VISIBLE','RESTRICTED')";
        if("FOLLOWING".equals(tab)){sql+=" AND EXISTS(SELECT 1 FROM community_follows f WHERE f.followee_id=p.author_id AND f.follower_id=?)";args.add(actor);}
        if("SAVED".equals(tab)){sql+=" AND EXISTS(SELECT 1 FROM community_saved_posts s WHERE s.post_id=p.id AND s.user_id=?)";args.add(actor);}
        if(q!=null&&!q.isBlank()){sql+=" AND p.status='VISIBLE' AND p.content LIKE ?";args.add("%"+content(q,100,false)+"%");}
        if("NEARBY".equals(tab) && (area==null||area.isBlank()))throw new AppException(INVALID_REQUEST);
        if(area!=null&&!area.isBlank()){sql+=" AND p.area=?";args.add(content(area,150,true));}
        Cursor c=decode(cursor);if(c!=null){sql+=" AND (p.created_at<? OR (p.created_at=? AND p.id<?))";args.add(c.at());args.add(c.at());args.add(c.id());}
        sql+=" ORDER BY p.created_at DESC,p.id DESC LIMIT 21";
        var ids=jdbc.queryForList(sql,String.class,args.toArray());boolean more=ids.size()>20;
        var items=ids.stream().limit(20).map(id->post(actor,id)).toList();
        Post last=items.isEmpty()?null:items.getLast();return new Slice<>(items,more?encode(last.createdAt(),last.id()):null,more);
    }
    @Override public Post post(String actor,String id) {
        var p=publicPost(actor,id,false);boolean own=Objects.equals(actor,p.get("author_id"));
        boolean full=own||"VISIBLE".equals(p.get("status"));
        var author=jdbc.queryForMap("SELECT full_name,avatar_url FROM users WHERE id=?",p.get("author_id"));
        String role=jdbc.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?",String.class,p.get("author_id")).stream().sorted().findFirst().orElse("TENANT");
        List<Media> media=full?jdbc.query("SELECT id,url FROM community_media WHERE post_id=? ORDER BY created_at,id",(r,n)->new Media(r.getString(1),r.getString(2)),id):List.of();
        List<ListingLink> links=full?jdbc.queryForList("SELECT listing_id FROM community_post_listings WHERE post_id=?",String.class,id).stream().map(lid->{
            var l=listings.findPublicForReview(lid,now());return new ListingLink(lid,l.map(v->v.getTitle()).orElse("Tin không còn khả dụng"),l.isPresent());}).toList():List.of();
        return new Post(id,(String)p.get("author_id"),(String)author.get("full_name"),(String)author.get("avatar_url"),role,
            full?(String)p.get("content"):null,(String)p.get("area"),(String)p.get("status"),((Number)p.get("version")).longValue(),(Boolean)p.get("pinned"),p.get("edited_at")!=null,
            p.get("created_at") instanceof LocalDateTime date ? date
                : ((Timestamp)p.get("created_at")).toLocalDateTime(),media,links,reactionCount("POST",id),
            jdbc.queryForObject("SELECT count(*) FROM community_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=? AND c.status='VISIBLE' AND c.deleted_at IS NULL AND u.status='ACTIVE' AND (c.parent_id IS NULL OR EXISTS(SELECT 1 FROM community_comments parent JOIN users pu ON pu.id=parent.author_id WHERE parent.id=c.parent_id AND parent.status='VISIBLE' AND pu.status='ACTIVE'))",Long.class,id),
            myReaction(actor,"POST",id),actor!=null&&count("SELECT count(*) FROM community_saved_posts WHERE user_id=? AND post_id=?",actor,id),
            actor!=null&&count("SELECT count(*) FROM community_follows WHERE follower_id=? AND followee_id=?",actor,p.get("author_id")));
    }
    private long reactionCount(String type,String id){return jdbc.queryForObject("SELECT count(*) FROM community_reactions WHERE target_type=? AND target_id=?",Long.class,type,id);}
    private String myReaction(String actor,String type,String id){var list=jdbc.queryForList("SELECT type FROM community_reactions WHERE user_id=? AND target_type=? AND target_id=?",String.class,actor,type,id);return list.isEmpty()?null:list.getFirst();}
    private void rate(String actor,String table,int max) {
        if(count("SELECT count(*) FROM users WHERE id=? AND created_at>?",actor,now().minusHours(24)))max=Math.max(1,max/2);
        if(jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE author_id=? AND created_at>?",Integer.class,actor,now().minusHours(1))>=max)throw new AppException(COMMUNITY_RATE_LIMITED);
    }
    @Override @Transactional public Post save(String actor,String id,PostInput b) {
        access.lockActor(actor);String text=content(b.content(),5000,false),area=content(b.area(),150,false);
        var media=b.mediaIds()==null?List.<String>of():b.mediaIds();var links=b.listingIds()==null?List.<String>of():b.listingIds();
        if(media.size()>10||links.size()>3||new HashSet<>(media).size()!=media.size()||new HashSet<>(links).size()!=links.size()||(text.isBlank()&&media.isEmpty()&&links.isEmpty()))throw new AppException(INVALID_REQUEST);
        long version=1;boolean hold=false;
        if(id==null) {
            rate(actor,"community_posts",5);id=UUID.randomUUID().toString();
            if(count("SELECT count(*) FROM community_posts WHERE author_id=? AND content=? AND created_at>?",actor,text,now().minusMinutes(10)))throw new AppException(COMMUNITY_RATE_LIMITED);
        } else {
            var old=row("community_posts",id,true);
            if(!actor.equals(old.get("author_id"))||old.get("deleted_at")!=null||"REMOVED".equals(old.get("status")))throw new AppException(UNAUTHORIZED);
            version=((Number)old.get("version")).longValue()+1;
            if(b.expectedVersion()==null||b.expectedVersion()!=version-1)throw new AppException(CONTENT_VERSION_CONFLICT);
            hold=Set.of("HIDDEN","PENDING").contains(old.get("status"));
        }
        for(String lid:links)if(listings.findPublicForReview(lid,now()).isEmpty())throw new AppException(LISTING_NOT_FOUND);
        for(String mid:media)if(!count("SELECT count(*) FROM community_media WHERE id=? AND owner_id=? AND (post_id IS NULL OR post_id=?)",mid,actor,id))throw new AppException(UNAUTHORIZED);
        var evaluation=moderation.evaluate("POST",text);String status=hold?"PENDING":evaluation.status();
        if(version==1)jdbc.update("INSERT INTO community_posts(id,author_id,content,area,status,created_at) VALUES(?,?,?,?,?,?)",id,actor,text,area,status,now());
        else jdbc.update("UPDATE community_posts SET content=?,area=?,status=?,version=?,edited_at=? WHERE id=?",text,area,status,version,now(),id);
        jdbc.update("UPDATE community_media SET post_id=NULL WHERE post_id=?",id);
        for(String mid:media)jdbc.update("UPDATE community_media SET post_id=? WHERE id=?",id,mid);
        jdbc.update("DELETE FROM community_post_listings WHERE post_id=?",id);
        for(String lid:links)jdbc.update("INSERT INTO community_post_listings VALUES(?,?)",id,lid);
        var recorded=hold?new Evaluation("PENDING","Nội dung đã chỉnh sửa cần được xem xét lại. "+evaluation.reason()):evaluation;
        moderation.record("POST",id,actor,version,text,recorded);return post(actor,id);
    }
    @Override public Media upload(String actor,MultipartFile file) {
        access.active(actor);if(file.getSize()>5*1024*1024)throw new AppException(IMAGE_INVALID);
        String id=UUID.randomUUID().toString();var image=images.upload("community/"+actor,file);
        try{jdbc.update("INSERT INTO community_media(id,owner_id,url,public_id,created_at) VALUES(?,?,?,?,?)",id,actor,image.url(),image.publicId(),now());}
        catch(RuntimeException e){images.delete(image.publicId());throw e;}
        return new Media(id,image.url());
    }
    @Override public Slice<Comment> comments(String actor,String post,String parent,String cursor) {
        publicPost(actor,post,false);
        if(parent!=null) {var p=row("community_comments",parent,false);if(!visible((String)p.get("status"))||!"ACTIVE".equals(p.get("author_status"))||!post.equals(p.get("post_id"))||p.get("parent_id")!=null)throw new AppException(INVALID_REQUEST);}
        String sql="SELECT c.*,u.full_name,u.avatar_url FROM community_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=? AND u.status='ACTIVE' AND (c.status IN ('VISIBLE','RESTRICTED') OR c.author_id=?)";
        var args=new ArrayList<Object>();args.add(post);args.add(actor);
        if(parent==null)sql+=" AND c.parent_id IS NULL AND (c.deleted_at IS NULL OR EXISTS(SELECT 1 FROM community_comments reply WHERE reply.parent_id=c.id AND reply.status='VISIBLE' AND reply.deleted_at IS NULL))";else{sql+=" AND c.parent_id=? AND c.deleted_at IS NULL";args.add(parent);}
        Cursor c=decode(cursor);if(c!=null){sql+=" AND (c.created_at>? OR (c.created_at=? AND c.id>?))";args.add(c.at());args.add(c.at());args.add(c.id());}
        sql+=" ORDER BY c.created_at,c.id LIMIT 21";
        var all=jdbc.query(sql,(r,n)->mapComment(actor,r),args.toArray());boolean more=all.size()>20;var items=all.stream().limit(20).toList();
        var last=items.isEmpty()?null:items.getLast();return new Slice<>(items,more?encode(last.createdAt(),last.id()):null,more);
    }
    private Comment mapComment(String actor,ResultSet r) throws SQLException {
        String id=r.getString("id"),status=r.getString("status");boolean deleted=r.getTimestamp("deleted_at")!=null;
        boolean full=!deleted && ("VISIBLE".equals(status)||Objects.equals(actor,r.getString("author_id")));
        return new Comment(id,r.getString("post_id"),r.getString("parent_id"),r.getString("author_id"),r.getString("full_name"),r.getString("avatar_url"),
            full?r.getString("content"):null,status,r.getLong("version"),r.getTimestamp("created_at").toLocalDateTime(),reactionCount("POST_COMMENT",id),myReaction(actor,"POST_COMMENT",id),deleted);
    }
    @Override @Transactional public Comment comment(String actor,String post,String id,CommentInput b) {
        access.lockActor(actor);var p=publicPost(actor,post,true);interact(p);String text=content(b.content(),2000,true);long version=1;String parent=b.parentId();boolean hold=false;
        if(id==null){rate(actor,"community_comments",30);id=UUID.randomUUID().toString();}
        else {var c=target(actor,"POST_COMMENT",id,true);if(!actor.equals(c.get("author_id"))||!post.equals(c.get("post_id")))throw new AppException(UNAUTHORIZED);
            version=((Number)c.get("version")).longValue()+1;if(b.expectedVersion()==null||b.expectedVersion()!=version-1)throw new AppException(CONTENT_VERSION_CONFLICT);
            parent=(String)c.get("parent_id");hold=!"VISIBLE".equals(c.get("status"));}
        if(parent!=null){var root=target(actor,"POST_COMMENT",parent,true);interact(root);if(!post.equals(root.get("post_id"))||root.get("parent_id")!=null)throw new AppException(INVALID_REQUEST);}
        var result=moderation.evaluate("POST_COMMENT",text);if(hold)result=new Evaluation("PENDING","Bình luận đã chỉnh sửa cần được duyệt lại. "+result.reason());
        if(version==1)jdbc.update("INSERT INTO community_comments(id,post_id,author_id,parent_id,content,status,created_at) VALUES(?,?,?,?,?,?,?)",id,post,actor,parent,text,result.status(),now());
        else jdbc.update("UPDATE community_comments SET content=?,status=?,version=?,edited_at=? WHERE id=?",text,result.status(),version,now(),id);
        moderation.record("POST_COMMENT",id,actor,version,text,result);
        if(version==1 && "VISIBLE".equals(result.status())) {
            var recipients=new HashSet<String>();recipients.add((String)p.get("author_id"));
            if(parent!=null)recipients.add((String)row("community_comments",parent,false).get("author_id"));
            recipients.remove(actor);
            for(String recipient:recipients)notifications.enqueue(recipient,com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType.SYSTEM,"Bình luận cộng đồng mới","Có thành viên vừa tham gia cuộc trò chuyện của bạn.","COMMUNITY_POST",post);
        }
        return jdbc.queryForObject("SELECT c.*,u.full_name,u.avatar_url FROM community_comments c JOIN users u ON u.id=c.author_id WHERE c.id=?",(r,n)->mapComment(actor,r),id);
    }
    @Override @Transactional public void delete(String actor,String type,String id) {
        access.active(actor);var r=target(actor,type,id,true);boolean allowed=actor.equals(r.get("author_id"))||access.admin(actor);
        if("POST_COMMENT".equals(type))allowed|=actor.equals(publicPost(actor,(String)r.get("post_id"),false).get("author_id"));
        if(!allowed)throw new AppException(UNAUTHORIZED);
        jdbc.update("UPDATE "+("POST".equals(type)?"community_posts":"community_comments")+" SET deleted_at=? WHERE id=?",now(),id);
    }
    @Override @Transactional public void reaction(String actor,String type,String id,String reaction) {
        access.active(actor);var t=target(actor,type,id,true);interact(t);
        if("POST_COMMENT".equals(type))interact(publicPost(actor,(String)t.get("post_id"),false));
        if(reaction==null)jdbc.update("DELETE FROM community_reactions WHERE target_type=? AND target_id=? AND user_id=?",type,id,actor);
        else {if(!Set.of("LIKE","LOVE","WOW").contains(reaction))throw new AppException(INVALID_REQUEST);
            jdbc.update("INSERT INTO community_reactions(target_type,target_id,user_id,type) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE type=?",type,id,actor,reaction,reaction);}
    }
    @Override @Transactional public void savePost(String actor,String id,boolean saved) {
        access.active(actor);publicPost(actor,id,true);
        if(saved)jdbc.update("INSERT IGNORE INTO community_saved_posts VALUES(?,?,?)",actor,id,now());else jdbc.update("DELETE FROM community_saved_posts WHERE user_id=? AND post_id=?",actor,id);
    }
    @Override @Transactional public void follow(String actor,String id,boolean followed) {
        access.active(actor);access.active(id);if(actor.equals(id))throw new AppException(INVALID_REQUEST);
        if(followed) {
            if(jdbc.update("INSERT IGNORE INTO community_follows VALUES(?,?)",actor,id)==1)notifications.enqueue(id,com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType.SYSTEM,"Người theo dõi mới","Có thành viên mới theo dõi bài viết của bạn.",null,null);
        } else jdbc.update("DELETE FROM community_follows WHERE follower_id=? AND followee_id=?",actor,id);
    }
    @Override @Transactional public void report(String actor,String type,String id,String reason) {
        access.lockActor(actor);var t=target(actor,type,id,true);if(actor.equals(t.get("author_id")))throw new AppException(CANNOT_REPORT_SELF);
        String text=content(reason,1000,true);long version=((Number)t.get("version")).longValue();
        if(count("SELECT count(*) FROM content_reports WHERE reporter_id=? AND created_at>?",actor,now().minusDays(1))) {
            if(jdbc.queryForObject("SELECT count(*) FROM content_reports WHERE reporter_id=? AND created_at>?",Integer.class,actor,now().minusDays(1))>=10)throw new AppException(COMMUNITY_RATE_LIMITED);
        }
        int added=jdbc.update("INSERT IGNORE INTO content_reports(id,target_type,target_id,content_version,reporter_id,reason,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),type,id,version,actor,text,now());
        if(added==0)return;
        long votes=jdbc.queryForObject("SELECT count(*) FROM content_reports r JOIN users u ON u.id=r.reporter_id WHERE r.target_type=? AND r.target_id=? AND r.content_version=? AND u.status='ACTIVE' AND u.email_verified_at IS NOT NULL AND u.created_at<=?",Long.class,type,id,version,now().minusHours(24));
        if(votes>=3)jdbc.update("UPDATE "+("POST".equals(type)?"community_posts":"community_comments")+" SET status='HIDDEN' WHERE id=?",id);
        String caseId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO content_moderation_cases(id,target_type,target_id,author_id,content_version,snapshot,reason,status,source,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",caseId,type,id,t.get("author_id"),version,t.get("content"),text,votes>=3?"HIDDEN":t.get("status"),"USER_REPORT",now());
    }
    @Override public String reveal(String actor,String type,String id) {
        access.active(actor);var t=target(actor,type,id,false);if(!"RESTRICTED".equals(t.get("status")))throw new AppException(INVALID_REQUEST);return (String)t.get("content");
    }
    @Override @Transactional public void pin(String actor,String id,boolean pinned) {access.requireAdmin(actor);interact(publicPost(actor,id,true));jdbc.update("UPDATE community_posts SET pinned=? WHERE id=?",pinned,id);}
    @Override @Transactional public void requestReview(String actor,String id,String reason) {
        access.lockActor(actor);var post=publicPost(actor,id,true);
        if(!actor.equals(post.get("author_id"))||"VISIBLE".equals(post.get("status")))throw new AppException(UNAUTHORIZED);
        if(count("SELECT count(*) FROM content_moderation_cases WHERE target_type='POST' AND target_id=? AND source='APPEAL' AND content_version=?",id,post.get("version")))throw new AppException(CONTENT_VERSION_CONFLICT);
        jdbc.update("INSERT INTO content_moderation_cases(id,target_type,target_id,author_id,content_version,snapshot,reason,status,source,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),"POST",id,actor,post.get("version"),post.get("content"),content(reason,1000,true),post.get("status"),"APPEAL",now());
    }
}
