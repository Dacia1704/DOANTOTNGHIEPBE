package com.tamdaptit.rentalmanagement.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorage {
    record UploadedAvatar(String url, String publicId) {}
    UploadedAvatar upload(String userId, MultipartFile file);
    void delete(String publicId);
}
