package com.tamdaptit.rentalmanagement.rental.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.service.RentalContractService;
import com.tamdaptit.rentalmanagement.rental.service.RentalService;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentalContractServiceImpl implements RentalContractService {
    private static final String BLANK = "..............................";
    private static final String[] DIGITS = {
        "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private final RentalService rentals;
    private final FileStorageService storage;
    private final RoomRepository rooms;
    private final RentalAgreementRepository agreements;
    private final RentalEvents events;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private JasperReport compiledReport;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Content download(
        String userId,
        String agreementId,
        boolean admin
    ) {
        // Authorization and readiness are checked inside a short read transaction.
        // Network I/O starts only after that transaction has ended.
        var reference = rentals.pdf(userId, agreementId, admin);
        try {
            byte[] bytes = storage.downloadPrivatePdf(reference.publicId());
            if (!sha256(bytes).equals(reference.hash())) {
                throw new AppException(ErrorCode.CONTRACT_CHANGED);
            }

            return new Content(bytes, reference.code());
        } catch (AppException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.PDF_GENERATION_FAILED);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.PDF_GENERATION_FAILED);
        }
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public RenderedPdf render(Map<String, String> snapshot) throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        for (
            String key : List.of(
                "agreementCode",
                "address",
                "landlordName",
                "landlordCitizenId",
                "landlordCitizenIdIssuedDate",
                "landlordCitizenIdIssuedPlace",
                "landlordAddress",
                "landlordPhone",
                "landlordEmail",
                "tenantName",
                "tenantCitizenId",
                "tenantCitizenIdIssuedDate",
                "tenantCitizenIdIssuedPlace",
                "tenantAddress",
                "tenantPhone",
                "tenantEmail",
                "propertyName",
                "roomNumber",
                "roomType",
                "areaM2",
                "maxOccupants",
                "occupantCount",
                "durationMonths",
                "paymentCycleMonths",
                "paymentDay",
                "feeUnits",
                "landlordConfirmedAt",
                "tenantConfirmation"
            )
        ) {
            parameters.put(key, text(snapshot.get(key)));
        }

        parameters.put("issuedDate", issuedDate(snapshot.get("issuedAt")));
        parameters.put("startDate", date(snapshot.get("startDate")));
        parameters.put("endDate", date(snapshot.get("endDate")));
        for (String key : List.of("price", "deposit", "electricityPrice", "waterPrice", "serviceFee")) {
            parameters.put(key, money(snapshot.get(key)));
        }

        parameters.put("priceInWords", moneyInWords(snapshot.get("price")));
        parameters.put("depositInWords", moneyInWords(snapshot.get("deposit")));
        parameters.put("terms", missing(snapshot.get("terms")) ? "" : text(snapshot.get("terms")));
        parameters.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag("vi-VN"));
        try (var out = new ByteArrayOutputStream()) {
            JasperPrint print = JasperFillManager.fillReport(report(), parameters, new JREmptyDataSource(1));
            var exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            var configuration = new SimplePdfExporterConfiguration();
            configuration.setMetadataTitle("Hợp đồng thuê phòng trọ " + parameters.get("agreementCode"));
            exporter.setConfiguration(configuration);
            exporter.exportReport();
            byte[] bytes = out.toByteArray();
            return new RenderedPdf(bytes, sha256(bytes));
        } catch (JRException ex) {
            throw new IOException("Cannot render rental contract with JasperReports", ex);
        }
    }

    @Override
    @Transactional
    public PdfClaim claim(String id) {
        var agreement = locked(id);
        var now = now();
        if (
            agreement.getStatus() != RentalAgreementStatus.PENDING_CONFIRMATION ||
                agreement.getContractSnapshot() == null ||
                agreement.getConfirmationExpiresAt() == null ||
                !agreement.getConfirmationExpiresAt().isAfter(now) ||
            agreement.getPdfStatus() == ContractPdfStatus.READY
        ) {
            return null;
        }

        if (
            agreement.getPdfStatus() == ContractPdfStatus.PROCESSING &&
                agreement.getPdfLeaseUntil() != null &&
            agreement.getPdfLeaseUntil().isAfter(now)
        ) {
            return null;
        }

        if (agreement.getPdfAttemptCount() >= 6) {
            if (agreement.getPdfStatus() == ContractPdfStatus.PROCESSING) {
                agreement.setPdfStatus(ContractPdfStatus.FAILED);
                agreement.setPdfLeaseUntil(null);
                agreement.setPdfError("Đã hết số lần thử tạo PDF. Vui lòng liên hệ hỗ trợ.");
            }

            return null;
        }

        if (
            agreement.getPdfStatus() != ContractPdfStatus.PROCESSING &&
                agreement.getPdfNextRetryAt() != null &&
            agreement.getPdfNextRetryAt().isAfter(now)
        ) {
            return null;
        }

        String token = UUID.randomUUID().toString();
        String publicId = "rental-management/contracts/" + id + "/" + token + ".pdf";
        agreement.setPdfStatus(ContractPdfStatus.PROCESSING);
        agreement.setPdfClaimToken(token);
        agreement.setPdfLeaseUntil(now.plusMinutes(5));
        agreement.setPdfAttemptCount(agreement.getPdfAttemptCount() + 1);
        jdbc.update(
            "INSERT INTO rental_pdf_assets(public_id,agreement_id,created_at) VALUES(?,?,?)",
            publicId,
            id,
            now
        );
        return new PdfClaim(id, token, publicId, agreement.getContractSnapshot());
    }

    @Override
    @Transactional
    public boolean complete(PdfClaim claim, String hash) {
        var agreement = locked(claim.id());
        if (
            !Objects.equals(agreement.getPdfClaimToken(), claim.token()) ||
                agreement.getPdfStatus() != ContractPdfStatus.PROCESSING ||
                agreement.getStatus() != RentalAgreementStatus.PENDING_CONFIRMATION ||
            !agreement.getConfirmationExpiresAt().isAfter(now())
        ) {
            return false;
        }

        agreement.setPdfPublicId(claim.publicId());
        agreement.setPdfUrl(null);
        agreement.setPdfSha256(hash);
        agreement.setPdfGeneratedAt(now());
        agreement.setPdfStatus(ContractPdfStatus.READY);
        agreement.setPdfError(null);
        agreement.setPdfLeaseUntil(null);
        events.emit(null, agreement.getTenant().getId(), agreement.getId(), "RENTAL_AGREEMENT", "PDF_READY", hash);
        events.emit(null, agreement.getLandlord().getId(), agreement.getId(), "RENTAL_AGREEMENT", "PDF_READY", hash);
        return true;
    }

    @Override
    @Transactional
    public void fail(PdfClaim claim) {
        var agreement = locked(claim.id());
        if (
            !Objects.equals(agreement.getPdfClaimToken(), claim.token()) ||
            agreement.getPdfStatus() != ContractPdfStatus.PROCESSING
        ) {
            return;
        }

        agreement.setPdfStatus(ContractPdfStatus.FAILED);
        agreement.setPdfError("Không thể tạo/lưu PDF. Vui lòng thử lại sau.");
        agreement.setPdfLeaseUntil(null);
        long delay = Math.min(1800, 30L << Math.min(agreement.getPdfAttemptCount(), 6));
        agreement.setPdfNextRetryAt(now().plusSeconds(delay));
        if (agreement.getPdfAttemptCount() == 1) {
            events.emit(null, agreement.getTenant().getId(), agreement.getId(), "RENTAL_AGREEMENT", "PDF_FAILED", null);
            events
                .emit(null, agreement.getLandlord().getId(), agreement.getId(), "RENTAL_AGREEMENT", "PDF_FAILED", null);
        }
    }

    private RentalAgreement locked(String id) {
        rooms.findByIdForUpdate(agreements.roomId(id).orElseThrow()).orElseThrow();
        return agreements.lock(id).orElseThrow();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).truncatedTo(ChronoUnit.SECONDS);
    }

    private synchronized JasperReport report() throws IOException, JRException {
        if (compiledReport == null) {
            try (var template = new ClassPathResource("reports/rental-contract.jrxml").getInputStream()) {
                compiledReport = JasperCompileManager.compileReport(template);
            }
        }

        return compiledReport;
    }

    private static boolean missing(String value) {
        return value == null || value.isBlank() || "Chưa cung cấp".equals(value.trim());
    }

    private static String text(String value) {
        return missing(value) ? BLANK
            : Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}&&[^\\n]]", " ")
                .trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LocalDate parseDate(String value) {
        if (missing(value)) {
            return null;
        }

        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String date(String value) {
        var date = parseDate(value);
        return date == null ? "...../...../........" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String issuedDate(String value) {
        var date = parseDate(value);
        return date == null ? "ngày ..... tháng ..... năm ........"
            : date.format(DateTimeFormatter.ofPattern("'ngày' dd 'tháng' MM 'năm' yyyy"));
    }

    private static BigDecimal amount(String value) {
        if (missing(value)) {
            return null;
        }

        try {
            return new BigDecimal(value.replace("VND", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String money(String value) {
        var amount = amount(value);
        if (amount == null) {
            return BLANK;
        }

        var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(Math.max(0, amount.stripTrailingZeros().scale()));
        return format.format(amount);
    }

    private static String moneyInWords(String value) {
        var amount = amount(value);
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 0) {
            return BLANK;
        }

        BigInteger number = amount.toBigIntegerExact();
        if (number.signum() == 0) {
            return "Không đồng";
        }

        List<Integer> groups = new ArrayList<>();
        while (number.signum() > 0) {
            BigInteger[] part = number.divideAndRemainder(BigInteger.valueOf(1000));
            groups.add(part[1].intValue());
            number = part[0];
        }

        var words = new StringJoiner(" ");
        for (int i = groups.size() - 1; i >= 0; i--) {
            int group = groups.get(i);
            if (group == 0) {
                continue;
            }

            words.add(readGroup(group, i < groups.size() - 1));
            String unit = switch (i % 3) {
            case 1 -> "nghìn";
            case 2 -> "triệu";
            default -> "";
            };
            if (!unit.isEmpty()) {
                words.add(unit);
            }

            for (int billion = 0; billion < i / 3; billion++) {
                words.add("tỷ");
            }
        }

        String result = words + " đồng";
        return Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    private static String readGroup(int number, boolean full) {
        int hundreds = number / 100;
        int tens = number / 10 % 10;
        int ones = number % 10;
        var words = new StringJoiner(" ");
        if (hundreds > 0 || full) {
            words.add(DIGITS[hundreds]);
            words.add("trăm");
        }

        if (tens > 1) {
            words.add(DIGITS[tens]);
            words.add("mươi");
        } else if (tens == 1) {
            words.add("mười");
        } else if (ones > 0 && (hundreds > 0 || full)) {
            words.add("lẻ");
        }

        if (ones > 0) {
            words.add(ones == 1 && tens > 1 ? "mốt" : ones == 5 && tens > 0 ? "lăm" : DIGITS[ones]);
        }

        return words.toString();
    }
}
