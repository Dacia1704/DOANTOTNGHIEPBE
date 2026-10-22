package com.tamdaptit.rentalmanagement.storage.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cau hinh Cloudinary cho module {@code storage}.
 *
 * <p>Gia tri doc tu bien moi truong {@code CLOUDINARY_CLOUD_NAME},
 * {@code CLOUDINARY_API_KEY}, {@code CLOUDINARY_API_SECRET}. Khong ghi secret
 * truc tiep vao source hay file cau hinh.
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret) {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);
        return new Cloudinary(config);
    }
}
