package com.tamdaptit.rentalmanagement.storage.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.storage.service.FileStorageService;
import com.tamdaptit.rentalmanagement.storage.service.ImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Luu anh va PDF rieng tu bang cung mot cau hinh Cloudinary.
 *
 * <p>Moi lai goi upload sinh mot {@code publicId} ngau nhien de tranh ghi de,
 * va tu lam sach tai nguyen neu upload that bai giua chung.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryFileStorage implements FileStorageService {
    private static final String ROOT = "rental-management";
    private static final String RESOURCE_IMAGE = "image";
    private static final String RESOURCE_RAW = "raw";
    private static final String PRIVATE_DELIVERY = "authenticated";
    private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

    private final Cloudinary cloudinary;
    private final ImageValidator validator;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public StoredImage upload(String folder, MultipartFile file) {
        return upload(folder, file, 5L * 1024 * 1024);
    }

    @Override
    public StoredImage upload(
        String folder,
        MultipartFile file,
        long maxBytes
    ) {
        byte[] bytes = validator.validate(file, maxBytes);
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
    public void uploadPrivatePdf(String publicId, byte[] bytes) throws IOException {
        ensureConfigured();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PDF_BYTES) {
            throw new IOException("PDF is empty or exceeds the size limit");
        }

        Map<?, ?> result = cloudinary.uploader()
            .upload(
                bytes,
                Map.of(
                    "public_id",
                    publicId,
                    "resource_type",
                    RESOURCE_RAW,
                    "type",
                    PRIVATE_DELIVERY,
                    "overwrite",
                    false
                )
            );
        if (!publicId.equals(result.get("public_id"))) {
            throw new IOException("Unexpected storage asset identifier");
        }
    }

    @Override
    public byte[] downloadPrivatePdf(String publicId) throws IOException, InterruptedException {
        ensureConfigured();
        String signed;
        try {
            signed = cloudinary.privateDownload(
                publicId,
                null,
                Map.of(
                    "resource_type",
                    RESOURCE_RAW,
                    "type",
                    PRIVATE_DELIVERY,
                    "expires_at",
                    System.currentTimeMillis() / 1000 + 60
                )
            );
        } catch (Exception ex) {
            throw new IOException("Cannot generate private PDF download URL", ex);
        }

        var request = HttpRequest.newBuilder(URI.create(signed))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Private document unavailable");
            }

            byte[] bytes = body.readNBytes(MAX_PDF_BYTES + 1);
            if (bytes.length > MAX_PDF_BYTES) {
                throw new IOException("Document exceeds size limit");
            }

            return bytes;
        }
    }

    @Override
    public void deletePrivatePdf(String publicId) throws IOException {
        ensureConfigured();
        Map<?, ?> result = cloudinary.uploader()
            .destroy(
                publicId,
                Map.of(
                    "resource_type",
                    RESOURCE_RAW,
                    "type",
                    PRIVATE_DELIVERY,
                    "invalidate",
                    true
                )
            );
        if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
            throw new IOException("Document cleanup failed");
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
        if (
            !(url instanceof String value) ||
                !value.startsWith("https://") ||
                value.length() > 255 ||
            !expectedPublicId.equals(result.get("public_id"))
        ) {
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
        if (
            cloudinary.config.cloudName == null ||
                cloudinary.config.cloudName.isBlank() ||
                cloudinary.config.apiKey == null ||
                cloudinary.config.apiKey.isBlank() ||
                cloudinary.config.apiSecret == null ||
            cloudinary.config.apiSecret.isBlank()
        ) {
            throw new AppException(ErrorCode.CLOUDINARY_NOT_CONFIGURED);
        }
    }
}
