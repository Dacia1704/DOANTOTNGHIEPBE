package com.tamdaptit.rentalmanagement.storage.service.impl;

import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.storage.service.ImageStorageService;
import com.tamdaptit.rentalmanagement.storage.service.ImageValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trien khai {@link ImageStorageService} tren Cloudinary.
 *
 * <p>Moi lai goi upload sinh mot {@code publicId} ngau nhien de tranh ghi de,
 * va tu lam sach tai nguyen neu upload that bai giua chung.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryImageStorage implements ImageStorageService {

    private static final String ROOT = "rental-management";
    private static final String RESOURCE_IMAGE = "image";
    private static final String RESOURCE_RAW = "raw";

    private final Cloudinary cloudinary;
    private final ImageValidator validator;

    @Override
    public StoredImage upload(String folder, MultipartFile file) {
        byte[] bytes = validator.validate(file);
        ensureConfigured();
        String publicId = newPublicId(folder);
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("public_id", publicId);
            options.put("resource_type", RESOURCE_IMAGE);
            options.put("overwrite", false);
            options.put("allowed_formats", List.of("jpg", "jpeg", "png"));
            return readResult(cloudinary.uploader().upload(bytes, options), publicId);
        } catch (IOException | RuntimeException ex) {
            cleanupQuietly(publicId);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public StoredImage uploadRaw(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        ensureConfigured();
        String publicId = newPublicId(folder);
        try {
            byte[] bytes;
            try (var stream = file.getInputStream()) {
                bytes = stream.readAllBytes();
            }
            Map<String, Object> options = new HashMap<>();
            options.put("public_id", publicId);
            options.put("resource_type", RESOURCE_RAW);
            options.put("overwrite", false);
            return readResult(cloudinary.uploader().upload(bytes, options), publicId);
        } catch (IOException | RuntimeException ex) {
            cleanupQuietly(publicId);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public List<StoredImage> uploadAll(String folder, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<StoredImage> stored = new ArrayList<>(files.size());
        try {
            for (MultipartFile file : files) {
                stored.add(upload(folder, file));
            }
        } catch (RuntimeException ex) {
            // Khong bo con doi mot phia: nhung anh da day len phai duoc goi lai.
            deleteAll(stored.stream().map(StoredImage::publicId).toList());
            throw ex;
        }
        return stored;
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        ensureConfigured();
        String resourceType = publicId.endsWith(".pdf") ? RESOURCE_RAW : RESOURCE_IMAGE;
        try {
            Map<String, Object> result = cloudinary.uploader()
                    .destroy(publicId, Map.of("resource_type", resourceType, "invalidate", true));
            String status = (String) result.get("result");
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new IOException("Cloudinary tra ve: " + status);
            }
        } catch (IOException | RuntimeException ex) {
            throw new AppException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    @Override
    public void deleteAll(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return;
        }
        for (String publicId : publicIds) {
            try {
                delete(publicId);
            } catch (AppException ex) {
                log.warn("Khong xoa duoc {}: {}", publicId, ex.getMessage());
            }
        }
    }

    @Override
    public void deleteByPrefix(String folder) {
        if (folder == null || folder.isBlank()) {
            return;
        }
        ensureConfigured();
        String prefix = ROOT + "/" + normalize(folder) + "/";
        try {
            cloudinary.api().deleteResourcesByPrefix(RESOURCE_IMAGE, Map.of("prefix", prefix));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    private StoredImage readResult(Map<?, ?> result, String expectedPublicId) throws IOException {
        Object url = result.get("secure_url");
        if (!(url instanceof String value)
                || !value.startsWith("https://")
                || value.length() > 255
                || !expectedPublicId.equals(result.get("public_id"))) {
            throw new IOException("Phan hoi Cloudinary khong hop le");
        }
        return new StoredImage(value, expectedPublicId);
    }

    private String newPublicId(String folder) {
        return ROOT + "/" + normalize(folder) + "/" + UUID.randomUUID();
    }

    private String normalize(String folder) {
        if (folder == null) {
            return "";
        }
        String trimmed = folder.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        if (trimmed.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thư mục lưu trữ không được để trống");
        }
        if (trimmed.contains("..")) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thư mục lưu trữ không hợp lệ");
        }
        return trimmed;
    }

    private void cleanupQuietly(String publicId) {
        try {
            delete(publicId);
        } catch (RuntimeException cleanup) {
            log.warn("Khong don duoc tai nguyen {}", publicId);
        }
    }

    private void ensureConfigured() {
        if (cloudinary.config.cloudName == null || cloudinary.config.cloudName.isBlank()
                || cloudinary.config.apiKey == null || cloudinary.config.apiKey.isBlank()
                || cloudinary.config.apiSecret == null || cloudinary.config.apiSecret.isBlank()) {
            throw new AppException(ErrorCode.CLOUDINARY_NOT_CONFIGURED);
        }
    }
}