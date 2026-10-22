package com.tamdaptit.rentalmanagement.storage.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lop luu tru dung chung cho toan he thong.
 *
 * <p>Khong co controller: module nao can se goi truc tiep tu service cua module do.
 * Moi anh luu vao mot folder rieng, duoc truyen qua tham so {@code folder}
 * (vi du {@code avatars/{userId}}, {@code rooms/{roomId}}, {@code report-evidences/{reportId}}).
 * Dung {@link StorageFolder} de tranh goi tay sai duong dan.
 *
 * <p>Cau hinh Cloudinary lay tu bien moi truong
 * {@code CLOUDINARY_CLOUD_NAME}, {@code CLOUDINARY_API_KEY}, {@code CLOUDINARY_API_SECRET}.
 */
public interface ImageStorageService {

    /** Ket qua upload: URL cong public + public id de xoa sau. */
    record StoredImage(String url, String publicId) {
    }

    /**
     * Tai lenh mot anh.
     *
     * @param folder duong dan thu muc trong bo luu tru, vi du {@code avatars/abc}
     * @throws com.tamdaptit.rentalmanagement.common.exception.AppException neu anh khong hop le
     *         hoac bo luu tru tra loi that bai
     */
    StoredImage upload(String folder, MultipartFile file);

    /**
     * Tai len nhieu anh cung luc vao cung mot folder.
     *
     * <p>Neu anh thu nhat that bai, nhung anh da day len truoc do se duoc goi lai
     * de khong bo roi tai nguyen.
     *
     * @throws com.tamdaptit.rentalmanagement.common.exception.AppException neu bat ky anh nao
     *         khong hop le hoac upload that bai
     */
    List<StoredImage> uploadAll(String folder, List<MultipartFile> files);

    /** Xoa mot anh theo public id. Thao tac nay khong phat sinh loi neu anh da khong ton tai. */
    void delete(String publicId);

    /** Xoa nhieu anh. Mot public id khong hop le khong chan phan con lai. */
    void deleteAll(List<String> publicIds);

    /**
     * Xoa toan bo anh co tien to {@code folder}. Dung khi xoa phong hoac hop dong.
     *
     * @param folder tien duong dan thu muc, vi du {@code rooms/abc}
     */
    void deleteByPrefix(String folder);

    /**
     * Tai mot tep PDF len bo luu tru voi {@code resource_type=raw}.
     * Dung cho hop dong thue.
     */
    StoredImage uploadRaw(String folder, MultipartFile file);
}