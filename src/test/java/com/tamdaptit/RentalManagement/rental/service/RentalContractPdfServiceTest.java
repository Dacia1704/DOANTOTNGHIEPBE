package com.tamdaptit.rentalmanagement.rental.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class RentalContractPdfServiceTest {
    private static final RentalContractPdfService RENDERER=new RentalContractPdfService();

    static Map<String,String> snapshot() {
        return new HashMap<>(Map.ofEntries(
                Map.entry("agreementCode","HD-DEMO-2026-001"),Map.entry("issuedAt","2026-11-01T10:30:00"),
                Map.entry("landlordName","Trần Thị Hồng"),Map.entry("landlordCitizenId","079123456789"),
                Map.entry("landlordPhone","0901234567"),Map.entry("landlordEmail","chutro@example.test"),
                Map.entry("tenantName","Nguyễn Văn An"),Map.entry("tenantCitizenId","012345678901"),
                Map.entry("tenantPhone","0912345678"),Map.entry("tenantEmail","nguoithue@example.test"),
                Map.entry("tenantAddress","12 Nguyễn Trãi, phường Bến Thành, Thành phố Hồ Chí Minh"),
                Map.entry("address","123 Lê Văn Sỹ, phường Nhiêu Lộc, Thành phố Hồ Chí Minh"),
                Map.entry("propertyName","Nhà trọ Hoa Mai"),Map.entry("roomNumber","A101"),
                Map.entry("roomType","Phòng có gác"),Map.entry("areaM2","25"),
                Map.entry("maxOccupants","4"),Map.entry("occupantCount","2"),Map.entry("durationMonths","12"),
                Map.entry("startDate","2026-11-15"),Map.entry("endDate","2027-11-14"),
                Map.entry("price","3000000.00 VND"),Map.entry("deposit","1500000.00 VND"),
                Map.entry("electricityPrice","3500 VND"),Map.entry("waterPrice","100000 VND"),
                Map.entry("serviceFee","50000 VND"),Map.entry("paymentCycleMonths","1"),
                Map.entry("feeUnits","Đơn vị điện/nước: chưa cung cấp; hai bên cần thống nhất."),
                Map.entry("landlordConfirmedAt","2026-11-01T10:30:00"),
                Map.entry("tenantConfirmation","Chờ người thuê xác nhận trong ứng dụng.")));
    }

    @Test void rendersVietnameseTemplateAndEmbedsFonts() throws Exception {
        var snapshot=snapshot();var original=Map.copyOf(snapshot);
        byte[] bytes=RENDERER.render(snapshot);
        assertEquals(original,snapshot);
        try(var pdf=Loader.loadPDF(bytes)) {
            String text=new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ");
            for(String expected:List.of("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM","HỢP ĐỒNG THUÊ PHÒNG TRỌ",
                    "BÊN CHO THUÊ PHÒNG TRỌ","BÊN THUÊ PHÒNG TRỌ","Trần Thị Hồng","Nguyễn Văn An",
                    "079123456789","012345678901","ngày 01 tháng 11 năm 2026",
                    "1. Nội dung thuê phòng trọ","2. Trách nhiệm Bên A","3. Trách nhiệm Bên B",
                    "4. Điều khoản thực hiện","3.000.000 đồng/tháng","Ba triệu đồng","Một triệu năm trăm nghìn đồng",
                    "15/11/2026","14/11/2027","không quá 4 người","ít nhất 01 tháng",
                    "Không phải chữ ký số được chứng thực","Chờ người thuê xác nhận"))
                assertTrue(text.contains(expected),expected);
            assertEquals("Hợp đồng thuê phòng trọ HD-DEMO-2026-001",pdf.getDocumentInformation().getTitle());
            assertEquals(2,pdf.getNumberOfPages());
            for(var page:pdf.getPages()) for(var name:page.getResources().getFontNames())
                assertTrue(page.getResources().getFont(name).isEmbedded());
        }
        // Explicit opt-in preview for visual QA; no personal data or running application required.
        writePreview("hop-dong-thue-phong-tro",bytes);
    }

    @Test void oldAndMissingSnapshotFieldsStayBlankWithoutInventingValues() throws Exception {
        byte[] bytes=RENDERER.render(Map.of());
        try(var pdf=Loader.loadPDF(bytes)) {
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains(RentalContractFormat.BLANK));
            assertTrue(text.contains("...../...../........"));
            assertFalse(text.contains("null"));assertFalse(text.contains("Thỏa thuận bổ sung:"));
        }
    }

    @Test void longPlainTextFlowsToAdditionalPagesWithoutEvaluatingMarkup() throws Exception {
        var snapshot=snapshot();
        snapshot.put("terms","<b>Giữ nguyên văn</b> & $P{price}\n"+"Thỏa thuận bổ sung về bảo quản tài sản. ".repeat(200)+"KẾT THÚC ĐIỀU KHOẢN");
        snapshot.put("tenantAddress","Địa chỉ dài có dấu tại Thành phố Hồ Chí Minh. ".repeat(11)+"HẾT ĐỊA CHỈ");
        byte[] bytes=RENDERER.render(snapshot);
        try(var pdf=Loader.loadPDF(bytes)) {
            String text=new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ");
            assertTrue(pdf.getNumberOfPages()>2);
            assertTrue(text.contains("<b>Giữ nguyên văn</b> & $P{price}"));
            assertTrue(text.contains("HẾT ĐỊA CHỈ"));assertTrue(text.contains("KẾT THÚC ĐIỀU KHOẢN"));
            assertTrue(text.contains("BÊN CHO THUÊ (BÊN A)"));
        }
        writePreview("hop-dong-noi-dung-dai",bytes);
    }

    private static void writePreview(String name,byte[] bytes) throws Exception {
        String directory=System.getProperty("rental.pdf.preview");
        if(directory==null) return;
        Path output=Path.of(directory);Files.createDirectories(output);
        Files.write(output.resolve(name+".pdf"),bytes);
        try(var pdf=Loader.loadPDF(bytes)) {
            var renderer=new PDFRenderer(pdf);
            for(int i=0;i<pdf.getNumberOfPages();i++)
                ImageIO.write(renderer.renderImageWithDPI(i,110),"PNG",output.resolve(name+"-"+(i+1)+".png").toFile());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,Không đồng","15,Mười lăm đồng","21,Hai mươi mốt đồng","105,Một trăm lẻ năm đồng",
            "1001,Một nghìn không trăm lẻ một đồng","1500000.00 VND,Một triệu năm trăm nghìn đồng",
            "3000000.00 VND,Ba triệu đồng","1000000000,Một tỷ đồng",
            "1000000000000,Một nghìn tỷ đồng"})
    void spellsWholeVndExactly(String amount,String expected) {
        assertEquals(expected,RentalContractFormat.moneyInWords(amount));
    }

    @Test void formattingDoesNotRoundOrGuessMissingDatesAndAmounts() {
        assertEquals("3.000.000",RentalContractFormat.money("3000000.00 VND"));
        assertEquals("1.234,5",RentalContractFormat.money("1234.50 VND"));
        assertEquals(RentalContractFormat.BLANK,RentalContractFormat.moneyInWords("1234.50 VND"));
        assertEquals(RentalContractFormat.BLANK,RentalContractFormat.money("Chưa cung cấp"));
        assertEquals("...../...../........",RentalContractFormat.date("invalid"));
    }
}
