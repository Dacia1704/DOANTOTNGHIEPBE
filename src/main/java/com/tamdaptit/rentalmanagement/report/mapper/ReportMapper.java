package com.tamdaptit.rentalmanagement.report.mapper;
import com.tamdaptit.rentalmanagement.report.dto.response.*;
import com.tamdaptit.rentalmanagement.report.entity.*;
import org.mapstruct.*;

@Mapper(componentModel="spring")
public interface ReportMapper {
    @Mapping(target="targetType", expression="java(report.getListing()==null ? com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType.LANDLORD : com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType.LISTING)")
    @Mapping(target="targetId", expression="java(report.getListing()==null ? report.getLandlord().getId() : report.getListing().getId())")
    @Mapping(target="landlordId", source="landlord.id")
    @Mapping(target="handledBy", source="handledBy.id")
    @Mapping(target="targetName", expression="java(report.getListing()==null ? report.getLandlord().getFullName() : report.getListing().getTitle())")
    @Mapping(target="handledByName", source="handledBy.fullName")
    @Mapping(target="originListingId", expression="java(report.getOriginListing()!=null ? report.getOriginListing().getId() : report.getListing()!=null ? report.getListing().getId() : null)")
    ReportResponse toResponse(Report report);

    @Mapping(target="contentUrl", expression="java(\"/api/reports/\" + evidence.getReport().getId() + \"/evidences/\" + evidence.getId() + \"/content\")")
    ReportEvidenceResponse toResponse(ReportEvidence evidence);
}
