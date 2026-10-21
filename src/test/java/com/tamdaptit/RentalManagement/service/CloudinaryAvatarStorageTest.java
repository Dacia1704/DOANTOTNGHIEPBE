package com.tamdaptit.RentalManagement.service;

import com.cloudinary.*;
import com.tamdaptit.rentalmanagement.exception.*;
import com.tamdaptit.rentalmanagement.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryAvatarStorageTest {
    private final Cloudinary cloud = spy(new Cloudinary(Map.of("cloud_name", "test", "api_key", "key", "api_secret", "secret")));
    private final CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(cloud);

    private MockMultipartFile png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "avatar.png", "image/png", bytes.toByteArray());
    }

    @Test void rejectsFakeImageAndOversizedFilesBeforeCallingCloudinary() {
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1])}) {
            assertEquals(ErrorCode.AVATAR_INVALID,
                    assertThrows(AppException.class, () -> storage.upload("user", file)).getErrorCode());
        }
        verify(cloud, never()).uploader();
    }

    @Test void missingCredentialsReturnsServiceUnavailable() throws Exception {
        var missing = new CloudinaryAvatarStorage(new Cloudinary(Map.of("cloud_name", "", "api_key", "", "api_secret", "")));
        assertEquals(ErrorCode.CLOUDINARY_NOT_CONFIGURED,
                assertThrows(AppException.class, () -> missing.upload("user", png())).getErrorCode());
    }

    @Test void uploadsValidatedFileWithUniquePublicIdAndImageResourceType() throws Exception {
        Uploader uploader = mock(Uploader.class);
        doReturn(uploader).when(cloud).uploader();
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(i -> {
            Map<?, ?> options = i.getArgument(1);
            assertEquals("image", options.get("resource_type"));
            assertEquals(false, options.get("overwrite"));
            return Map.of("secure_url", "https://res.cloudinary.com/test/image/upload/avatar.png", "public_id", options.get("public_id"));
        });
        var first = storage.upload("user", png());
        var second = storage.upload("user", png());
        assertTrue(first.publicId().startsWith("rental-management/avatars/user/"));
        assertNotEquals(first.publicId(), second.publicId());
    }

    @Test void cloudinaryFailureReturnsGatewayError() throws Exception {
        Uploader uploader = mock(Uploader.class);
        doReturn(uploader).when(cloud).uploader();
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("remote failure"));
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));
        assertEquals(ErrorCode.AVATAR_UPLOAD_FAILED, assertThrows(AppException.class, () -> storage.upload("user", png())).getErrorCode());
    }
}
