package com.tamdaptit.rentalmanagement.storage.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Kiem tra tinh hop le cua anh truoc khi day len bo luu tru.
 *
 * <p>Khong tin vao {@code Content-Type} do client gui: noi dung thuc duoc doc bang
 * {@link ImageIO} de xac nhan dinh dang va kich thuoc thật.
 */
@Component
public class ImageValidator {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    /**
     * Doc va kiem tra anh, tra ve noi dung byte da doc.
     *
     * @throws AppException {@code IMAGE_INVALID} neu rong, qua lon, sai dinh dang
     *         hoac vuot qua kich thuoc cho phep
     */
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }

        String contentType = file.getContentType();
        if (!JPEG.equals(contentType) && !PNG.equals(contentType)) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }

        byte[] bytes = read(file);
        if (bytes.length > MAX_BYTES) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }

        String detected = probe(bytes);
        if (detected == null) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }
        // File thuc su la JPEG thi content type phai la image/jpeg (va nguoc lai).
        if (!detected.equals(contentType)) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }

        BufferedImage image = decode(bytes);
        if (image == null) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }
        if (image.getWidth() < 1 || image.getHeight() < 1
                || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }
        return bytes;
    }

    private byte[] read(MultipartFile file) {
        try (var stream = file.getInputStream()) {
            return stream.readNBytes((int) MAX_BYTES + 1);
        } catch (IOException | RuntimeException ex) {
            throw new AppException(ErrorCode.IMAGE_INVALID);
        }
    }

    private String probe(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if ("jpeg".equals(format) || "jpg".equals(format)) {
                    return JPEG;
                }
                if ("png".equals(format)) {
                    return PNG;
                }
                return null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private BufferedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}
