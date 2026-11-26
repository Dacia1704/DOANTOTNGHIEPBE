package com.tamdaptit.rentalmanagement.community.controller;

import java.security.Principal;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Author;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Comment;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.CommentInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ListingLink;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Post;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.PostInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ReactionInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ReportInput;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Slice;
import com.tamdaptit.rentalmanagement.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityService service;

    private String actor(Principal p) {
        return p == null ? null : p.getName();
    }

    @GetMapping("/listings")
    public ApiResponse<List<ListingLink>> listings(@RequestParam(defaultValue = "") String q) {
        return ApiResponse.success(service.listingChoices(q));
    }

    @GetMapping("/feed")
    public ApiResponse<Slice<Post>> feed(
        Principal p,
        @RequestParam(defaultValue = "LATEST") String tab,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String q
    ) {
        return ApiResponse.success(service.feed(actor(p), tab, cursor, q));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<Post> post(Principal p, @PathVariable String id) {
        return ApiResponse.success(service.post(actor(p), id));
    }

    @GetMapping("/authors/{id}")
    public ApiResponse<Author> author(Principal p, @PathVariable String id) {
        return ApiResponse.success(service.author(actor(p), id));
    }

    @GetMapping("/authors/{id}/posts")
    public ApiResponse<Slice<Post>> authorPosts(
        Principal p,
        @PathVariable String id,
        @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.success(service.authorPosts(actor(p), id, cursor));
    }

    @PostMapping(value = "/posts", consumes = "application/json")
    public ApiResponse<Post> create(Principal p, @Valid @RequestBody PostInput b) {
        return ApiResponse.success(service.save(actor(p), null, b));
    }

    @PutMapping(value = "/posts/{id}", consumes = "application/json")
    public ApiResponse<Post> update(
        Principal p,
        @PathVariable String id,
        @Valid @RequestBody PostInput b
    ) {
        return ApiResponse.success(service.save(actor(p), id, b));
    }

    @PostMapping(value = "/posts", consumes = "multipart/form-data")
    public ApiResponse<Post> createWithImages(
        Principal p,
        @Valid @RequestPart("post") PostInput b,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.success(service.save(actor(p), null, b, files));
    }

    @PutMapping(value = "/posts/{id}", consumes = "multipart/form-data")
    public ApiResponse<Post> updateWithImages(
        Principal p,
        @PathVariable String id,
        @Valid @RequestPart("post") PostInput b,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.success(service.save(actor(p), id, b, files));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(Principal p, @PathVariable String id) {
        service.delete(actor(p), "POST", id);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<Slice<Comment>> comments(
        Principal p,
        @PathVariable String id,
        @RequestParam(required = false) String parent,
        @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.success(service.comments(actor(p), id, parent, cursor));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<Comment> comment(
        Principal p,
        @PathVariable String id,
        @Valid @RequestBody CommentInput b
    ) {
        return ApiResponse.success(service.comment(actor(p), id, null, b));
    }

    @PutMapping("/posts/{post}/comments/{id}")
    public ApiResponse<Comment> editComment(
        Principal p,
        @PathVariable String post,
        @PathVariable String id,
        @Valid @RequestBody CommentInput b
    ) {
        return ApiResponse.success(service.comment(actor(p), post, id, b));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(Principal p, @PathVariable String id) {
        service.delete(actor(p), "POST_COMMENT", id);
        return ApiResponse.success(null);
    }

    @PutMapping("/content/{type}/{id}/reaction")
    public ApiResponse<Void> reaction(
        Principal p,
        @PathVariable String type,
        @PathVariable String id,
        @Valid @RequestBody ReactionInput b
    ) {
        service.reaction(actor(p), type, id, b.type());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/content/{type}/{id}/reaction")
    public ApiResponse<Void> unreact(
        Principal p,
        @PathVariable String type,
        @PathVariable String id
    ) {
        service.reaction(actor(p), type, id, null);
        return ApiResponse.success(null);
    }

    @PostMapping("/content/{type}/{id}/report")
    public ApiResponse<Void> report(
        Principal p,
        @PathVariable String type,
        @PathVariable String id,
        @Valid @RequestBody ReportInput b
    ) {
        service.report(actor(p), type, id, b.reason());
        return ApiResponse.success(null);
    }

    @PostMapping("/content/{type}/{id}/reveal")
    public ApiResponse<String> reveal(
        Principal p,
        @PathVariable String type,
        @PathVariable String id
    ) {
        return ApiResponse.success(service.reveal(actor(p), type, id));
    }

    @PutMapping("/posts/{id}/save")
    public ApiResponse<Void> save(Principal p, @PathVariable String id) {
        service.savePost(actor(p), id, true);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/posts/{id}/save")
    public ApiResponse<Void> unsave(Principal p, @PathVariable String id) {
        service.savePost(actor(p), id, false);
        return ApiResponse.success(null);
    }

    @PutMapping("/users/{id}/follow")
    public ApiResponse<Void> follow(Principal p, @PathVariable String id) {
        service.follow(actor(p), id, true);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/users/{id}/follow")
    public ApiResponse<Void> unfollow(Principal p, @PathVariable String id) {
        service.follow(actor(p), id, false);
        return ApiResponse.success(null);
    }

    @PutMapping("/posts/{id}/pin")
    public ApiResponse<Void> pin(
        Principal p,
        @PathVariable String id,
        @RequestParam boolean pinned
    ) {
        service.pin(actor(p), id, pinned);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{id}/appeal")
    public ApiResponse<Void> appeal(
        Principal p,
        @PathVariable String id,
        @Valid @RequestBody ReportInput b
    ) {
        service.requestReview(actor(p), id, b.reason());
        return ApiResponse.success(null);
    }
}
