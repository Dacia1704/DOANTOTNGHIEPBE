package com.tamdaptit.rentalmanagement.rental.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Fills the trusted JRXML template with plain text from the immutable server snapshot only. */
@Service
public class RentalContractPdfService {
    private JasperReport compiledReport;

    public byte[] render(Map<String,String> snapshot) throws IOException {
        Map<String,Object> parameters=new HashMap<>();
        for(String key:List.of("agreementCode","address","landlordName","landlordCitizenId",
                "landlordCitizenIdIssuedDate","landlordCitizenIdIssuedPlace","landlordAddress",
                "landlordPhone","landlordEmail","tenantName","tenantCitizenId","tenantCitizenIdIssuedDate",
                "tenantCitizenIdIssuedPlace","tenantAddress","tenantPhone","tenantEmail","propertyName",
                "roomNumber","roomType","areaM2","maxOccupants","occupantCount","durationMonths",
                "paymentCycleMonths","paymentDay","feeUnits","landlordConfirmedAt","tenantConfirmation")) {
            parameters.put(key,text(snapshot.get(key)));
        }
        parameters.put("issuedDate",RentalContractFormat.issuedDate(snapshot.get("issuedAt")));
        parameters.put("startDate",RentalContractFormat.date(snapshot.get("startDate")));
        parameters.put("endDate",RentalContractFormat.date(snapshot.get("endDate")));
        for(String key:List.of("price","deposit","electricityPrice","waterPrice","serviceFee"))
            parameters.put(key,RentalContractFormat.money(snapshot.get(key)));
        parameters.put("priceInWords",RentalContractFormat.moneyInWords(snapshot.get("price")));
        parameters.put("depositInWords",RentalContractFormat.moneyInWords(snapshot.get("deposit")));
        parameters.put("terms",missing(snapshot.get("terms"))?"":text(snapshot.get("terms")));
        parameters.put(JRParameter.REPORT_LOCALE,Locale.forLanguageTag("vi-VN"));
        try(var out=new ByteArrayOutputStream()) {
            JasperPrint print=JasperFillManager.fillReport(report(),parameters,new JREmptyDataSource(1));
            var exporter=new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            var configuration=new SimplePdfExporterConfiguration();
            configuration.setMetadataTitle("Hợp đồng thuê phòng trọ "+parameters.get("agreementCode"));
            exporter.setConfiguration(configuration);
            exporter.exportReport();
            return out.toByteArray();
        } catch(JRException ex) {
            throw new IOException("Cannot render rental contract with JasperReports",ex);
        }
    }

    private synchronized JasperReport report() throws IOException,JRException {
        if(compiledReport==null) {
            try(var template=new ClassPathResource("reports/rental-contract.jrxml").getInputStream()) {
                compiledReport=JasperCompileManager.compileReport(template);
            }
        }
        return compiledReport;
    }

    static boolean missing(String value) {
        return value==null||value.isBlank()||"Chưa cung cấp".equals(value.trim());
    }

    static String text(String value) {
        return missing(value)?RentalContractFormat.BLANK:Normalizer.normalize(value,Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}&&[^\\n]]"," ").trim();
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
