package com.tamdaptit.rentalmanagement.report.dto.request;
import jakarta.validation.constraints.*;
public record ReportResolutionRequest(@NotBlank @Size(max=5000) String resolutionNote,
    @Size(max=36) String relatedListingId) {
    public ReportResolutionRequest(String resolutionNote) {this(resolutionNote,null);}
}
