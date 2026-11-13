package com.tamdaptit.rentalmanagement.report.dto.response;
import java.util.List;
public record ReportSanctionOptionsResponse(int nextWarningLevel,List<ListingOption> listings) {
    public record ListingOption(String id,String title,String status) {}
}
