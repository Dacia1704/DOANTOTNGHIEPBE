package com.tamdaptit.rentalmanagement.rental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.job.RentalJob;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.repository.RentalRequestRepository;
import com.tamdaptit.rentalmanagement.rental.service.RentalContractService;
import com.tamdaptit.rentalmanagement.rental.service.RentalService;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalContractServiceImpl;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalEvents;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.storage.service.FileStorageService;
import com.tamdaptit.rentalmanagement.storage.service.ImageValidator;
import com.tamdaptit.rentalmanagement.storage.service.impl.CloudinaryFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RentalContractServiceTest {
    private final RentalService rentals = mock(RentalService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RentalAgreementRepository agreements = mock(RentalAgreementRepository.class);
    private final RentalEvents events = mock(RentalEvents.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);
    private final RentalContractServiceImpl contracts = new RentalContractServiceImpl(
        rentals,
        storage,
        rooms,
        agreements,
        events,
        jdbc,
        clock
    );
    private final RentalContractService.PdfClaim claim = new RentalContractService.PdfClaim(
        "agreement",
        "token",
        "rental-management/contracts/agreement/token.pdf",
        "{}"
    );

    @Test
    void rendersVietnameseContractAndReturnsTheHashOfItsExactBytes() throws Exception {
        var snapshot = Map.ofEntries(
            Map.entry("agreementCode", "HD-TEST-001"),
            Map.entry("landlordName", "Nguyễn Văn Chủ"),
            Map.entry("tenantName", "Trần Thị Khách"),
            Map.entry("address", "123 đường Nguyễn Huệ, Thành phố Hồ Chí Minh"),
            Map.entry("issuedAt", "2026-11-26T10:00:00"),
            Map.entry("startDate", "2026-12-01"),
            Map.entry("endDate", "2027-11-30"),
            Map.entry("price", "2500000 VND"),
            Map.entry("deposit", "2500000 VND"),
            Map.entry("terms", "Hai bên thống nhất bảo quản tài sản và thanh toán đúng kỳ.")
        );

        var pdf = contracts.render(snapshot);

        assertThat(new String(pdf.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.bytes()).hasSizeGreaterThan(1000);
        assertThat(pdf.sha256()).isEqualTo(hash(pdf.bytes()));
        verifyNoInteractions(storage, rooms, agreements, events, jdbc);
        String preview = System.getProperty("rental.pdf.preview");
        if (preview != null) {
            Path directory = Files.createDirectories(Path.of(preview));
            Files.write(directory.resolve("rental-contract.pdf"), pdf.bytes());
        }
    }

    @Test
    void downloadChecksAccessBeforeFetchingAndChecksTheHash() throws Exception {
        byte[] bytes = "%PDF-synthetic-test".getBytes(StandardCharsets.US_ASCII);
        when(rentals.pdf("tenant", "agreement", false))
            .thenReturn(new RentalService.PdfReference(claim.publicId(), hash(bytes), "HD-001"));
        when(storage.downloadPrivatePdf(claim.publicId())).thenReturn(bytes);

        var content = contracts.download("tenant", "agreement", false);

        assertThat(content.bytes()).isEqualTo(bytes);
        assertThat(content.code()).isEqualTo("HD-001");
        var order = inOrder(rentals, storage);
        order.verify(rentals).pdf("tenant", "agreement", false);
        order.verify(storage).downloadPrivatePdf(claim.publicId());
    }

    @Test
    void unauthorizedDownloadNeverReachesStorage() {
        when(rentals.pdf("stranger", "agreement", false))
            .thenThrow(new AppException(ErrorCode.NOT_AGREEMENT_PARTY));

        assertThatThrownBy(() -> contracts.download("stranger", "agreement", false))
            .isInstanceOfSatisfying(
                AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_AGREEMENT_PARTY)
            );
        verifyNoInteractions(storage);
    }

    @Test
    void changedPdfCannotBeDownloaded() throws Exception {
        when(rentals.pdf("tenant", "agreement", false))
            .thenReturn(new RentalService.PdfReference(claim.publicId(), "wrong-hash", "HD-001"));
        when(storage.downloadPrivatePdf(claim.publicId())).thenReturn(new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> contracts.download("tenant", "agreement", false))
            .isInstanceOfSatisfying(
                AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_CHANGED)
            );
    }

    @Test
    void claimLocksRoomBeforeAgreementAndRecordsCleanupIntent() {
        var agreement = pendingAgreement();

        var result = contracts.claim("agreement");

        assertThat(result).isNotNull();
        assertThat(result.publicId()).startsWith("rental-management/contracts/agreement/").endsWith(".pdf");
        assertThat(agreement.getPdfStatus()).isEqualTo(ContractPdfStatus.PROCESSING);
        assertThat(agreement.getPdfAttemptCount()).isEqualTo(1);
        assertThat(agreement.getPdfLeaseUntil()).isEqualTo(now.plusMinutes(5));
        var order = inOrder(rooms, agreements, jdbc);
        order.verify(agreements).roomId("agreement");
        order.verify(rooms).findByIdForUpdate("room");
        order.verify(agreements).lock("agreement");
        order.verify(jdbc).update(anyString(), eq(result.publicId()), eq("agreement"), eq(now));
        verifyNoInteractions(storage);
    }

    @Test
    void expiredAgreementCannotClaimPdfWork() {
        var agreement = pendingAgreement();
        agreement.setConfirmationExpiresAt(now);

        assertThat(contracts.claim("agreement")).isNull();
        verifyNoInteractions(jdbc, storage);
    }

    @Test
    void activeLeasePreventsASecondPdfAttempt() {
        var agreement = pendingAgreement();
        agreement.setPdfStatus(ContractPdfStatus.PROCESSING);
        agreement.setPdfLeaseUntil(now.plusMinutes(1));

        assertThat(contracts.claim("agreement")).isNull();
        verifyNoInteractions(jdbc, storage);
    }

    @Test
    void staleClaimCannotPublishOverANewerAttempt() {
        var agreement = pendingAgreement();
        agreement.setPdfStatus(ContractPdfStatus.PROCESSING);
        agreement.setPdfClaimToken("newer-token");

        assertThat(contracts.complete(claim, "hash")).isFalse();
        assertThat(agreement.getPdfPublicId()).isNull();
        verifyNoInteractions(events);
    }

    @Test
    void successfulAttemptPublishesPrivateIdentifierAndHash() {
        var agreement = pendingAgreement();
        agreement.setPdfStatus(ContractPdfStatus.PROCESSING);
        agreement.setPdfClaimToken(claim.token());

        assertThat(contracts.complete(claim, "hash")).isTrue();
        assertThat(agreement.getPdfStatus()).isEqualTo(ContractPdfStatus.READY);
        assertThat(agreement.getPdfPublicId()).isEqualTo(claim.publicId());
        assertThat(agreement.getPdfSha256()).isEqualTo("hash");
        assertThat(agreement.getPdfUrl()).isNull();
        verify(events).emit(null, "tenant", "agreement", "RENTAL_AGREEMENT", "PDF_READY", "hash");
        verify(events).emit(null, "owner", "agreement", "RENTAL_AGREEMENT", "PDF_READY", "hash");
    }

    @Test
    void failedAttemptSchedulesRetryAndReleasesItsLease() {
        var agreement = pendingAgreement();
        agreement.setPdfStatus(ContractPdfStatus.PROCESSING);
        agreement.setPdfClaimToken(claim.token());
        agreement.setPdfAttemptCount(1);
        agreement.setPdfLeaseUntil(now.plusMinutes(5));

        contracts.fail(claim);

        assertThat(agreement.getPdfStatus()).isEqualTo(ContractPdfStatus.FAILED);
        assertThat(agreement.getPdfLeaseUntil()).isNull();
        assertThat(agreement.getPdfNextRetryAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void uploadFailureIsRetriedWithoutPublishingPdf() throws Exception {
        var pdfService = mock(RentalContractService.class);
        var job = new RentalJob(
            rentals,
            agreements,
            mock(RentalRequestRepository.class),
            pdfService,
            storage,
            jdbc,
            clock
        );
        byte[] bytes = new byte[] { 1, 2, 3 };
        when(pdfService.claim("agreement")).thenReturn(claim);
        when(rentals.readSnapshot("{}")).thenReturn(Map.of());
        when(pdfService.render(Map.of())).thenReturn(new RentalContractService.RenderedPdf(bytes, "hash"));
        doThrow(new IOException("Upload failed")).when(storage).uploadPrivatePdf(claim.publicId(), bytes);

        job.process("agreement");

        verify(pdfService).fail(claim);
        verify(pdfService, never()).complete(any(), anyString());
    }

    @Test
    void staleUploadIsDeletedThroughPrivateStorage() throws Exception {
        var pdfService = mock(RentalContractService.class);
        var job = new RentalJob(
            rentals,
            agreements,
            mock(RentalRequestRepository.class),
            pdfService,
            storage,
            jdbc,
            clock
        );
        byte[] bytes = new byte[] { 1, 2, 3 };
        when(pdfService.claim("agreement")).thenReturn(claim);
        when(rentals.readSnapshot("{}")).thenReturn(Map.of());
        when(pdfService.render(Map.of())).thenReturn(new RentalContractService.RenderedPdf(bytes, "hash"));
        when(pdfService.complete(claim, "hash")).thenReturn(false);

        job.process("agreement");

        verify(storage).deletePrivatePdf(claim.publicId());
        verify(pdfService, never()).fail(any());
    }

    @Test
    void sharedStorageUploadsPdfAsAuthenticatedRawAndKeepsImageUploadsWorking() throws Exception {
        var cloudinary = cloudinary();
        var uploader = mock(Uploader.class);
        doReturn(uploader).when(cloudinary).uploader();
        var validator = mock(ImageValidator.class);
        var sharedStorage = new CloudinaryFileStorage(cloudinary, validator);
        byte[] bytes = "%PDF-test".getBytes(StandardCharsets.US_ASCII);
        when(uploader.upload(eq(bytes), anyMap())).thenReturn(Map.of("public_id", claim.publicId()));

        sharedStorage.uploadPrivatePdf(claim.publicId(), bytes);

        verify(uploader).upload(
            bytes,
            Map.of(
                "public_id",
                claim.publicId(),
                "resource_type",
                "raw",
                "type",
                "authenticated",
                "overwrite",
                false
            )
        );
        verifyNoInteractions(validator);

        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[] { 1 });
        when(validator.validate(image, 5L * 1024 * 1024)).thenReturn(new byte[] { 2 });
        when(uploader.upload(aryEq(new byte[] { 2 }), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            assertThat(options.get("resource_type")).isEqualTo("image");
            assertThat(options.containsKey("type")).isFalse();
            return Map.of("public_id", options.get("public_id"), "secure_url", "https://example.com/image.png");
        });

        assertThat(sharedStorage.upload("avatars/test", image).url()).isEqualTo("https://example.com/image.png");
    }

    @Test
    void sharedStorageDeletesAuthenticatedPdfIdempotently() throws Exception {
        var cloudinary = cloudinary();
        var uploader = mock(Uploader.class);
        doReturn(uploader).when(cloudinary).uploader();
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));
        var sharedStorage = new CloudinaryFileStorage(cloudinary, mock(ImageValidator.class));

        sharedStorage.deletePrivatePdf(claim.publicId());

        verify(uploader)
            .destroy(claim.publicId(), Map.of("resource_type", "raw", "type", "authenticated", "invalidate", true));
    }

    @Test
    void oversizedPdfNeverReachesCloudinary() {
        var cloudinary = cloudinary();
        var uploader = mock(Uploader.class);
        doReturn(uploader).when(cloudinary).uploader();
        var sharedStorage = new CloudinaryFileStorage(cloudinary, mock(ImageValidator.class));

        assertThatThrownBy(() -> sharedStorage.uploadPrivatePdf(claim.publicId(), new byte[10 * 1024 * 1024 + 1]))
            .isInstanceOf(IOException.class);
        verifyNoInteractions(uploader);
    }

    private RentalAgreement pendingAgreement() {
        var tenant = new User();
        tenant.setId("tenant");
        var owner = new User();
        owner.setId("owner");
        var agreement = RentalAgreement.builder()
            .id("agreement")
            .tenant(tenant)
            .landlord(owner)
            .status(RentalAgreementStatus.PENDING_CONFIRMATION)
            .contractSnapshot("{}")
            .confirmationExpiresAt(now.plusHours(48))
            .build();
        when(agreements.roomId("agreement")).thenReturn(Optional.of("room"));
        when(rooms.findByIdForUpdate("room")).thenReturn(Optional.of(new Room()));
        when(agreements.lock("agreement")).thenReturn(Optional.of(agreement));
        return agreement;
    }

    private Cloudinary cloudinary() {
        return spy(
            new Cloudinary(Map.of("cloud_name", "unit-test", "api_key", "unit-test", "api_secret", "unit-test"))
        );
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
