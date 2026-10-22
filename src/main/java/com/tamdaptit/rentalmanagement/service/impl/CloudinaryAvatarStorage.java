package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.service.AvatarStorage;

import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryAvatarStorage implements AvatarStorage {
    private final Cloudinary cloudinary;

    @Override
    public UploadedAvatar upload(String userId, MultipartFile file) {
        byte[] bytes = validate(file);
        ensureConfigured();
        String publicId = "rental-management/avatars/" + userId + "/" + UUID.randomUUID();
        try {
            var result = cloudinary.uploader().upload(bytes, Map.of(
                    "public_id", publicId, "resource_type", "image", "overwrite", false,
                    "allowed_formats", List.of("jpg", "jpeg", "png")));
            Object url = result.get("secure_url");
            if (!(url instanceof String value) || !value.startsWith("https://") || value.length() > 255
                    || !publicId.equals(result.get("public_id"))) {
                throw new IOException("Invalid Cloudinary upload response");
            }
            return new UploadedAvatar((String) url, publicId);
        } catch (IOException | RuntimeException ex) {
            try { delete(publicId); } catch (RuntimeException cleanup) { log.warn("Unable to clean up failed avatar upload {}", publicId); }
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024
                || !("image/jpeg".equals(file.getContentType()) || "image/png".equals(file.getContentType()))) {
            throw new AppException(ErrorCode.AVATAR_INVALID);
        }
        try {
            byte[] bytes;
            try (var stream = file.getInputStream()) { bytes = stream.readNBytes(5 * 1024 * 1024 + 1); }
            if (bytes.length > 5 * 1024 * 1024) throw new IOException("Oversized image");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Invalid image");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    boolean jpeg = format.equals("jpeg") || format.equals("jpg");
                    if (!(jpeg || format.equals("png"))
                            || !(jpeg ? "image/jpeg" : "image/png").equals(file.getContentType())
                            || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                            || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096
                            || reader.read(0) == null) throw new IOException("Invalid image dimensions/type");
                } finally { reader.dispose(); }
            }
            return bytes;
        } catch (IOException | RuntimeException ex) {
            throw new AppException(ErrorCode.AVATAR_INVALID);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        ensureConfigured();
        try {
            var result = cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image", "invalidate", true));
            if (!("ok".equals(result.get("result")) || "not found".equals(result.get("result")))) {
                throw new IOException("Cloudinary deletion failed");
            }
        } catch (IOException | RuntimeException ex) { throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED); }
    }

    private void ensureConfigured() {
        if (cloudinary.config.cloudName == null || cloudinary.config.cloudName.isBlank()
                || cloudinary.config.apiKey == null || cloudinary.config.apiKey.isBlank()
                || cloudinary.config.apiSecret == null || cloudinary.config.apiSecret.isBlank()) {
            throw new AppException(ErrorCode.CLOUDINARY_NOT_CONFIGURED);
        }
    }
}
