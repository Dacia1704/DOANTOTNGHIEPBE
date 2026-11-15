package com.tamdaptit.rentalmanagement.community.service;

import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CommunityService {
    List<ListingLink> listingChoices(String query);
    Slice<Post> feed(String actor,String tab,String cursor,String q);
    Post post(String actor,String id);
    Author author(String actor,String id);
    Slice<Post> authorPosts(String actor,String id,String cursor);
    Post save(String actor,String id,PostInput input);
    Post save(String actor,String id,PostInput input,List<MultipartFile> files);
    void delete(String actor,String type,String id);
    Slice<Comment> comments(String actor,String post,String parent,String cursor);
    Comment comment(String actor,String post,String id,CommentInput input);
    void reaction(String actor,String type,String id,String reaction);
    void savePost(String actor,String id,boolean saved);
    void follow(String actor,String id,boolean followed);
    void report(String actor,String type,String id,String reason);
    String reveal(String actor,String type,String id);
    void pin(String actor,String id,boolean pinned);
    void requestReview(String actor,String id,String reason);
}
