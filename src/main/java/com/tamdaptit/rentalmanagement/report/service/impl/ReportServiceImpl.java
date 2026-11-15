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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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
        report.setOriginListing(resolveOrigin(actor,report,body));
        return mapper.toResponse(reports.saveAndFlush(report));
    }

    private com.tamdaptit.rentalmanagement.listing.entity.Listing resolveOrigin(String actor,Report report,ReportCreateRequest body) {
        String listingId=body.originListingId();
        if(listingId!=null && listingId.isBlank() || body.rentalAgreementId()!=null && body.rentalAgreementId().isBlank())
            throw new AppException(INVALID_REQUEST);
        if(report.getListing()!=null) {
            if(body.rentalAgreementId()!=null || listingId!=null && !listingId.equals(report.getListing().getId()))
                throw new AppException(INVALID_REQUEST);
            return report.getListing();
        }
        com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement agreement=null;
        if(body.rentalAgreementId()!=null) {
            agreement=agreements.findById(body.rentalAgreementId()).orElseThrow(()->new AppException(INVALID_REQUEST));
            if(!actor.equals(agreement.getTenant().getId()) || !report.getLandlord().getId().equals(agreement.getLandlord().getId())
                || !CONFIRMED.contains(agreement.getStatus()) || agreement.getTenantConfirmedAt()==null || agreement.getLandlordConfirmedAt()==null)
                throw new AppException(INVALID_REQUEST);
            if(agreement.getRentalRequest()!=null) {
                String originalId=agreement.getRentalRequest().getListing().getId();
                if(listingId!=null && !listingId.equals(originalId)) throw new AppException(INVALID_REQUEST);
                listingId=originalId;
            }
        }
        if(listingId==null) return null;
        var listing=listings.findById(listingId).orElseThrow(()->new AppException(LISTING_NOT_FOUND));
        if(!report.getLandlord().getId().equals(listing.getProperty().getLandlord().getId())
            || agreement!=null && !agreement.getRoom().getProperty().getId().equals(listing.getProperty().getId()))
            throw new AppException(INVALID_REQUEST);
        return listing;
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
        if(!actor.equals(report.getTenant().getId()) && !hasPermission(viewer,"REPORT_READ"))
            throw new AppException(UNAUTHORIZED);
        return new ReportDetailResponse(mapper.toResponse(report),
            evidences.findByReport_IdOrderByCreatedAtAscIdAsc(id).stream().map(mapper::toResponse).toList(),
            ReportListingResponse.from(report.getOriginListing()!=null?report.getOriginListing():report.getListing()),
            report.getSanctionedListingId()==null?null:listings.findById(report.getSanctionedListingId()).map(ReportListingResponse::from).orElse(null));
    }
    @Override
    public ReportListingResponse listingDetail(String actor,String reportId,String listingId) {
        permission(actor,"REPORT_READ");
        var report=reports.findById(reportId).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        var listing=listings.findById(listingId).orElseThrow(()->new AppException(LISTING_NOT_FOUND));
        boolean recorded=report.getOriginListing()!=null && listingId.equals(report.getOriginListing().getId())
            || report.getListing()!=null && listingId.equals(report.getListing().getId())
            || listingId.equals(report.getSanctionedListingId());
        if(!recorded && !report.getLandlord().getId().equals(listing.getProperty().getLandlord().getId()))
            throw new AppException(LISTING_NOT_FOUND);
        return ReportListingResponse.from(listing);
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
        permission(actor,"REPORT_READ");
        return page(reports.findAll(filter(null,status,reason),pageable(page,size)));
    }
    @Override
    public Map<ReportStatus,Long> summary(String actor) {
        permission(actor,"REPORT_READ");
        var result=new EnumMap<ReportStatus,Long>(ReportStatus.class);
        for(var status:ReportStatus.values()) result.put(status,0L);
        for(var count:reports.counts()) result.put(count.getStatus(),count.getTotal());
        return result;
    }
    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReportResponse assign(String actor,String id) {
        var admin=permission(actor,"REPORT_HANDLE");
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
        permission(actor,"REPORT_HANDLE");
        if(outcome!=ReportStatus.RESOLVED && outcome!=ReportStatus.REJECTED) throw new AppException(INVALID_REQUEST);
        String note=required(body.resolutionNote(),5000);
        // Owner -> report matches listing mutations and serializes strikes across different reports.
        String ownerId=reports.landlordId(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        var owner=users.findByIdForUpdate(ownerId).orElseThrow(()->new AppException(USER_NOT_FOUND));
        var report=reports.lock(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(report.getStatus()!=ReportStatus.PROCESSING) throw new AppException(REPORT_INVALID_TRANSITION);
        if(report.getHandledBy()==null || !actor.equals(report.getHandledBy().getId()))
            throw new AppException(REPORT_NOT_ASSIGNED_TO_YOU);
        if(outcome==ReportStatus.RESOLVED) sanction(report,owner,body.relatedListingId());
        report.setStatus(outcome);
        report.setResolutionNote(note);
        report.setResolvedAt(LocalDateTime.now(clock));
        notifyTenant(report,outcome==ReportStatus.RESOLVED?"Báo cáo của bạn đã được giải quyết.":"Báo cáo của bạn đã bị từ chối. Vui lòng xem kết quả xử lý.");
        reports.flush();
        return mapper.toResponse(report);
    }
    @Override
    public ReportSanctionOptionsResponse sanctionOptions(String actor,String id) {
        permission(actor,"REPORT_READ");
        permission(actor,"REPORT_HANDLE");
        var report=reports.findById(id).orElseThrow(()->new AppException(REPORT_NOT_FOUND));
        if(report.getStatus()!=ReportStatus.PROCESSING) throw new AppException(REPORT_INVALID_TRANSITION);
        if(report.getHandledBy()==null || !actor.equals(report.getHandledBy().getId()))
            throw new AppException(REPORT_NOT_ASSIGNED_TO_YOU);
        return new ReportSanctionOptionsResponse(Math.min(report.getLandlord().getConfirmedReportCount()+1,3),
            listings.findAllByProperty_Landlord_IdOrderByCreatedAtDesc(report.getLandlord().getId()).stream()
                .map(l->new ReportSanctionOptionsResponse.ListingOption(l.getId(),l.getTitle(),l.getStatus().name())).toList());
    }
    private void sanction(Report report,User owner,String relatedListingId) {
        int count=jdbc.queryForObject("SELECT confirmed_report_count FROM users WHERE id=?",Integer.class,owner.getId());
        int level=Math.min(count+1,3);
        if(level==3 && hasRole(owner,RoleCode.ADMIN)) throw new AppException(PROTECTED_ADMIN);
        var origin=report.getOriginListing()!=null?report.getOriginListing():report.getListing();
        String listingId=origin==null?relatedListingId:origin.getId();
        if(origin!=null && relatedListingId!=null && !relatedListingId.equals(listingId))
            throw new AppException(INVALID_REQUEST,"Không được đổi tin đăng bị báo cáo");
        if(level==2 && (listingId==null || listingId.isBlank()))
            throw new AppException(REPORT_RELATED_LISTING_REQUIRED);
        if(level==2) {
            int changed=jdbc.update("""
                UPDATE listings l JOIN properties p ON p.id=l.property_id
                SET l.status='HIDDEN',l.initial_review=0,l.updated_at=?
                WHERE l.id=? AND p.landlord_id=?
                """,LocalDateTime.now(clock),listingId,owner.getId());
            if(changed!=1) throw new AppException(INVALID_REQUEST,"Tin đăng không còn thuộc chủ trọ bị báo cáo");
            jdbc.update("""
                UPDATE listings l JOIN properties p ON p.id=l.property_id
                SET l.priority_level=0,l.package_priority_level=0,l.service_subscription_period_id=NULL,
                    l.priority_suppressed=1,l.updated_at=?
                WHERE p.landlord_id=?
                """,LocalDateTime.now(clock),owner.getId());
            jdbc.update("""
                UPDATE boost_allocations a JOIN listings l ON l.id=a.listing_id
                JOIN properties p ON p.id=l.property_id
                SET a.active=0 WHERE p.landlord_id=? AND a.active=1
                """,owner.getId());
            report.setSanctionedListingId(listingId);
        }
        jdbc.update("UPDATE users SET confirmed_report_count=confirmed_report_count+1,updated_at=? WHERE id=?",
            LocalDateTime.now(clock),owner.getId());
        if(level==3) {
            jdbc.update("UPDATE users SET status='BLOCKED',auth_version=auth_version+1 WHERE id=?",owner.getId());
        }
        report.setWarningLevel(level);
        report.setSanctionAction(level==1?"WARNING":level==2?"HIDE_LISTING_AND_SUPPRESS_PRIORITY":"BLOCK_ACCOUNT");
        String body=level==1?"Bạn đã bị cảnh cáo lần 1 do báo cáo vi phạm được xác nhận."
            :level==2?"Cảnh cáo lần 2: tin đăng liên quan đã bị ẩn; ưu tiên các tin còn lại của bạn đã về 0."
            :"Cảnh cáo lần 3: tài khoản của bạn đã bị khóa.";
        notifications.enqueue(owner.getId(),NotificationType.REPORT,"Thông báo xử lý vi phạm",body,
            level==2?"LISTING":"SYSTEM",level==2?listingId:owner.getId());
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
    private User permission(String actor,String code) {
        var viewer=user(actor,null);
        if(!hasPermission(viewer,code)) throw new AppException(UNAUTHORIZED);
        return viewer;
    }
    private boolean hasPermission(User user,String code) {
        return user.getUserRoles().stream().flatMap(ur->ur.getRole().getRolePermissions().stream())
            .anyMatch(rp->code.equals(rp.getPermission().getCode()));
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
