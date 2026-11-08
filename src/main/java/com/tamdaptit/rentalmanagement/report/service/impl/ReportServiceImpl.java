package com.tamdaptit.rentalmanagement.report.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.*;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.report.dto.request.*;
import com.tamdaptit.rentalmanagement.report.dto.response.*;
import com.tamdaptit.rentalmanagement.report.entity.Report;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import com.tamdaptit.rentalmanagement.report.mapper.ReportMapper;
import com.tamdaptit.rentalmanagement.report.repository.*;
import com.tamdaptit.rentalmanagement.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ReportServiceImpl implements ReportService {
    private static final List<ReportStatus> OPEN=List.of(ReportStatus.PENDING,ReportStatus.PROCESSING);
    private static final List<RentalAgreementStatus> CONFIRMED=List.of(
        RentalAgreementStatus.ACTIVE,RentalAgreementStatus.EXPIRED,RentalAgreementStatus.TERMINATED);
    private final ReportRepository reports;
    private final ReportEvidenceRepository evidences;
    private final UserRepository users;
    private final ListingRepository listings;
    private final RentalAgreementRepository agreements;
    private final NotificationPublisher notifications;
    private final ReportMapper mapper;
    private final Clock clock;

    @Override
    public List<ReportReason> reasons(ReportTargetType type) {
        if(type==null) throw new AppException(INVALID_REQUEST);
        return Arrays.stream(ReportReason.values()).filter(reason->reason.supports(type)).toList();
    }

    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReportResponse create(String actor,ReportCreateRequest body) {
        // All create paths lock the reporter, so concurrent target checks cannot both succeed.
        var tenant=users.findByIdForUpdate(actor).orElseThrow(()->new AppException(USER_NOT_FOUND));
        user(actor,RoleCode.TENANT);
        if(tenant.getStatus()!=UserStatus.ACTIVE) throw new AppException(UNAUTHORIZED);
        if(body.targetType()==null || body.reasonCode()==null || !body.reasonCode().supports(body.targetType()))
            throw new AppException(INVALID_REQUEST);
        String description=text(body.description(),5000);
        if(body.reasonCode()==ReportReason.OTHER && (description==null || description.isBlank()))
            throw new AppException(INVALID_REQUEST);
        var report=Report.builder().tenant(tenant).reasonCode(body.reasonCode().name()).description(description).build();
        boolean duplicate;
        if(body.targetType()==ReportTargetType.LISTING) {
            var listing=listings.findPublicForReview(body.targetId(),LocalDateTime.now(clock))
                .orElseThrow(()->new AppException(LISTING_NOT_FOUND));
            report.setListing(listing);
            report.setLandlord(listing.getProperty().getLandlord());
            duplicate=reports.existsByTenant_IdAndListing_IdAndStatusIn(actor,listing.getId(),OPEN);
        } else {
            var landlord=users.findById(body.targetId()).orElseThrow(()->new AppException(USER_NOT_FOUND));
            if(actor.equals(landlord.getId())) throw new AppException(CANNOT_REPORT_SELF);
            if(!agreements.hasConfirmedRelationship(actor,landlord.getId(),CONFIRMED))
                throw new AppException(REPORT_CONTRACT_REQUIRED);
            report.setLandlord(landlord);
            duplicate=reports.existsByTenant_IdAndLandlord_IdAndListingIsNullAndStatusIn(actor,landlord.getId(),OPEN);
        }
        if(actor.equals(report.getLandlord().getId())) throw new AppException(CANNOT_REPORT_SELF);
        if(duplicate) throw new AppException(REPORT_ALREADY_OPEN);
        return mapper.toResponse(reports.saveAndFlush(report));
    }

    @Override
    public PageResponse<ReportResponse> mine(String actor,ReportStatus status,int page,int size) {
        user(actor,RoleCode.TENANT);
        return page(reports.findAll(filter(actor,status,null),pageable(page,size)));
    }
    @Override
    public ReportDetailResponse detail(String actor,String id) {
        var viewer=user(actor,null);
        var report=reports.findById(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(!actor.equals(report.getTenant().getId()) && !hasRole(viewer,RoleCode.ADMIN))
            throw new AppException(UNAUTHORIZED);
        return new ReportDetailResponse(mapper.toResponse(report),
            evidences.findByReport_IdOrderByCreatedAtAscIdAsc(id).stream().map(mapper::toResponse).toList());
    }
    @Override
    public void checkUpload(String actor,String id) {
        user(actor,RoleCode.TENANT);
        var report=reports.findById(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(!actor.equals(report.getTenant().getId())) throw new AppException(UNAUTHORIZED);
        if(report.getStatus()!=ReportStatus.PENDING) throw new AppException(REPORT_NOT_PENDING);
        if(evidences.countByReport_Id(id)>=5) throw new AppException(IMAGE_TOO_MANY);
    }
    @Override
    public PageResponse<ReportResponse> admin(String actor,ReportStatus status,ReportReason reason,int page,int size) {
        user(actor,RoleCode.ADMIN);
        return page(reports.findAll(filter(null,status,reason),pageable(page,size)));
    }
    @Override
    public Map<ReportStatus,Long> summary(String actor) {
        user(actor,RoleCode.ADMIN);
        var result=new EnumMap<ReportStatus,Long>(ReportStatus.class);
        for(var status:ReportStatus.values()) result.put(status,0L);
        for(var count:reports.counts()) result.put(count.getStatus(),count.getTotal());
        return result;
    }
    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReportResponse assign(String actor,String id) {
        var admin=user(actor,RoleCode.ADMIN);
        var report=reports.lock(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(report.getStatus()!=ReportStatus.PENDING) throw new AppException(REPORT_INVALID_TRANSITION);
        report.setStatus(ReportStatus.PROCESSING);
        report.setHandledBy(admin);
        notifyTenant(report,"Báo cáo của bạn đã được tiếp nhận xử lý.");
        reports.flush();
        return mapper.toResponse(report);
    }
    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReportResponse close(String actor,String id,ReportStatus outcome,ReportResolutionRequest body) {
        user(actor,RoleCode.ADMIN);
        if(outcome!=ReportStatus.RESOLVED && outcome!=ReportStatus.REJECTED) throw new AppException(INVALID_REQUEST);
        String note=required(body.resolutionNote(),5000);
        var report=reports.lock(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(report.getStatus()!=ReportStatus.PROCESSING) throw new AppException(REPORT_INVALID_TRANSITION);
        if(report.getHandledBy()==null || !actor.equals(report.getHandledBy().getId()))
            throw new AppException(REPORT_NOT_ASSIGNED_TO_YOU);
        report.setStatus(outcome);
        report.setResolutionNote(note);
        report.setResolvedAt(LocalDateTime.now(clock));
        notifyTenant(report,outcome==ReportStatus.RESOLVED?"Báo cáo của bạn đã được giải quyết.":"Báo cáo của bạn đã bị từ chối. Vui lòng xem kết quả xử lý.");
        reports.flush();
        return mapper.toResponse(report);
    }
    private void notifyTenant(Report report,String body) {
        notifications.enqueue(report.getTenant().getId(),NotificationType.REPORT,
            "Cập nhật báo cáo",body,"REPORT",report.getId());
    }
    private User user(String id,RoleCode role) {
        var user=users.findByIdWithRolesAndPermissions(id).orElseThrow(()->new AppException(USER_NOT_FOUND));
        if(user.getStatus()!=UserStatus.ACTIVE || (role!=null && !hasRole(user,role))) throw new AppException(UNAUTHORIZED);
        return user;
    }
    private boolean hasRole(User user,RoleCode role) {
        return user.getUserRoles().stream().anyMatch(ur->ur.getRole().getCode()==role);
    }
    private Specification<Report> filter(String tenant,ReportStatus status,ReportReason reason) {
        return (root,query,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(tenant!=null) predicates.add(cb.equal(root.get("tenant").get("id"),tenant));
            if(status!=null) predicates.add(cb.equal(root.get("status"),status));
            if(reason!=null) predicates.add(cb.equal(root.get("reasonCode"),reason.name()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    private Pageable pageable(int page,int size) {
        if(page<0 || size<1 || size>50) throw new AppException(INVALID_REQUEST);
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id"));
    }
    private PageResponse<ReportResponse> page(Page<Report> page) {
        return PageResponse.<ReportResponse>builder().currentPage(page.getNumber()).pageSize(page.getSize())
            .totalPages(page.getTotalPages()).totalElements(page.getTotalElements())
            .data(page.getContent().stream().map(mapper::toResponse).toList()).build();
    }
    private String text(String value,int max) {
        if(value==null) return null;
        if(value.length()>max) throw new AppException(INVALID_REQUEST);
        return value.strip();
    }
    private String required(String value,int max) {
        String result=text(value,max);
        if(result==null || result.isBlank()) throw new AppException(INVALID_REQUEST);
        return result;
    }
}
