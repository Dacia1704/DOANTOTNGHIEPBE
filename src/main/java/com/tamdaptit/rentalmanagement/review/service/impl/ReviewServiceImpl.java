package com.tamdaptit.rentalmanagement.review.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.*;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.review.dto.request.*;
import com.tamdaptit.rentalmanagement.review.dto.response.*;
import com.tamdaptit.rentalmanagement.review.entity.*;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;
import com.tamdaptit.rentalmanagement.review.mapper.ReviewMapper;
import com.tamdaptit.rentalmanagement.review.repository.*;
import com.tamdaptit.rentalmanagement.review.service.ReviewService;
import jakarta.persistence.criteria.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ReviewServiceImpl implements ReviewService {
    private static final List<RentalAgreementStatus> CONFIRMED = List.of(
        RentalAgreementStatus.ACTIVE, RentalAgreementStatus.EXPIRED, RentalAgreementStatus.TERMINATED);
    private final RoomReviewRepository rooms;
    private final LandlordReviewRepository landlords;
    private final TenantReviewRepository tenants;
    private final RentalAgreementRepository agreements;
    private final ListingRepository listings;
    private final UserRepository users;
    private final ReviewMapper mapper;
    private final Clock clock;
    private final com.tamdaptit.rentalmanagement.room.repository.RoomRepository roomRepository;

    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewResponse create(String actor, ReviewType type, ReviewCreateRequest body) {
        user(actor, type==ReviewType.TENANT ? RoleCode.LANDLORD : RoleCode.TENANT);
        if (body.rating()==null || body.rating()<1 || body.rating()>5) throw new AppException(INVALID_RATING);
        // Match rental's room -> agreement lock order; avoid deadlock with checkout/deletion.
        String roomId=agreements.roomId(body.rentalAgreementId()).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND));
        roomRepository.findByIdForUpdate(roomId).orElseThrow(()->new AppException(ROOM_NOT_FOUND));
        var agreement=agreements.lock(body.rentalAgreementId()).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND));
        requireAuthor(actor,type,agreement);
        if (!eligible(agreement)) throw new AppException(REVIEW_NOT_ELIGIBLE);
        if (existing(type,agreement.getId())!=null) throw new AppException(duplicate(type));
        String comment=text(body.comment(),2000);
        return switch(type) {
            case ROOM -> mapper.toResponse(rooms.saveAndFlush(RoomReview.builder().rentalAgreement(agreement)
                .tenant(agreement.getTenant()).room(agreement.getRoom()).rating(body.rating()).comment(comment).build()));
            case LANDLORD -> mapper.toResponse(landlords.saveAndFlush(LandlordReview.builder().rentalAgreement(agreement)
                .tenant(agreement.getTenant()).landlord(agreement.getLandlord()).rating(body.rating()).comment(comment).build()));
            case TENANT -> mapper.toResponse(tenants.saveAndFlush(TenantReview.builder().rentalAgreement(agreement)
                .tenant(agreement.getTenant()).landlord(agreement.getLandlord()).rating(body.rating()).comment(comment).build()));
        };
    }

    @Override
    public ReviewEligibilityResponse eligibility(String actor, String agreementId) {
        var viewer=user(actor,null);
        var agreement=agreements.findById(agreementId).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND));
        if (!actor.equals(agreement.getTenant().getId()) && !actor.equals(agreement.getLandlord().getId()))
            throw new AppException(NOT_AGREEMENT_PARTY);
        return new ReviewEligibilityResponse(option(viewer,agreement,ReviewType.ROOM),
            option(viewer,agreement,ReviewType.LANDLORD),option(viewer,agreement,ReviewType.TENANT));
    }

    private ReviewEligibilityResponse.Eligibility option(User viewer,RentalAgreement agreement,ReviewType type) {
        String id=existing(type,agreement.getId());
        boolean author=viewer.getId().equals(type==ReviewType.TENANT?agreement.getLandlord().getId():agreement.getTenant().getId());
        RoleCode role=type==ReviewType.TENANT?RoleCode.LANDLORD:RoleCode.TENANT;
        String reason=!author || !hasRole(viewer,role) ? "NOT_REVIEW_AUTHOR"
            : agreement.getTenant().getId().equals(agreement.getLandlord().getId()) ? "CANNOT_REVIEW_SELF"
            : !eligible(agreement) ? "REVIEW_NOT_ELIGIBLE" : id!=null ? duplicate(type).name() : null;
        // Never disclose the other party's hidden review ID through eligibility.
        return new ReviewEligibilityResponse.Eligibility(reason==null,author?id:null,reason);
    }

    @Override
    public PageResponse<ReviewDetailResponse> mine(String actor, ReviewType type,int page,int size) {
        user(actor,null);
        var pageable=pageable(page,size);
        return switch(type) {
            case ROOM -> page(rooms.findAll(filter(null,"tenant",actor),pageable),this::detail);
            case LANDLORD -> page(landlords.findAll(filter(null,"tenant",actor),pageable),this::detail);
            case TENANT -> page(tenants.findAll(filter(null,"landlord",actor),pageable),this::detail);
        };
    }

    @Override
    public PageResponse<ReceivedReviewResponse> received(String actor,ReviewType type,int page,int size) {
        user(actor,RoleCode.LANDLORD);
        var pageable=pageable(page,size);
        return switch(type) {
            case ROOM -> page(rooms.findAll(filter(false,"rentalAgreement.landlord",actor),pageable),
                entry->receivedResponse(entry,actor));
            case LANDLORD -> page(landlords.findAll(filter(false,"rentalAgreement.landlord",actor),pageable),
                entry->receivedResponse(entry,actor));
            case TENANT -> throw new AppException(INVALID_REQUEST);
        };
    }

    private ReceivedReviewResponse receivedResponse(ReviewEntry entry,String actor) {
        var agreement=entry.getRentalAgreement();
        var room=agreement.getRoom();
        var property=room.getProperty();
        return new ReceivedReviewResponse(response(entry),targetId(entry),targetName(entry),
            agreement.getId(),agreement.getAgreementCode(),room.getId(),room.getRoomNumber(),
            property.getId(),property.getName(),
            !Boolean.TRUE.equals(entry.getHidden()) && actor.equals(agreement.getLandlord().getId()));
    }

    @Override
    public PageResponse<ReviewResponse> listing(String listingId,String roomId,int page,int size) {
        Listing listing=publicListing(listingId);
        requireRoom(listing,roomId);
        Specification<RoomReview> spec=roomId==null?filter(false,"room.property",listing.getProperty().getId()):filter(false,"room",roomId);
        return page(rooms.findAll(spec,pageable(page,size)),this::response);
    }

    @Override
    public ReviewSummaryResponse listingSummary(String listingId,String roomId) {
        Listing listing=publicListing(listingId);
        requireRoom(listing,roomId);
        return summary(rooms.ratings(roomId,listing.getProperty().getId()));
    }

    private void requireRoom(Listing listing,String roomId) {
        if (roomId!=null && !roomsBelongToProperty(roomId,listing.getProperty().getId()))
            throw new AppException(ROOM_NOT_FOUND);
    }

    private boolean roomsBelongToProperty(String roomId,String propertyId) {
        return roomRepository.existsByIdAndProperty_Id(roomId,propertyId);
    }

    private Listing publicListing(String id) {
        return listings.findPublicForReview(id,LocalDateTime.now(clock)).orElseThrow(()->new AppException(LISTING_NOT_FOUND));
    }

    @Override
    public PageResponse<ReviewResponse> landlord(String landlordId,int page,int size) {
        publicLandlord(landlordId);
        return page(landlords.findAll(filter(false,"landlord",landlordId),pageable(page,size)),this::response);
    }
    @Override
    public ReviewSummaryResponse landlordSummary(String landlordId) {
        publicLandlord(landlordId);
        return summary(landlords.ratings(landlordId));
    }
    private void publicLandlord(String id) {
        if (!listings.hasPublicListing(id,LocalDateTime.now(clock))) throw new AppException(USER_NOT_FOUND);
    }

    @Override
    public PageResponse<ReviewResponse> tenant(String actor,String tenantId,int page,int size) {
        tenantAccess(actor,tenantId);
        return page(tenants.findAll(filter(false,"tenant",tenantId),pageable(page,size)),this::response);
    }
    @Override
    public ReviewSummaryResponse tenantSummary(String actor,String tenantId) {
        tenantAccess(actor,tenantId);
        return summary(tenants.ratings(tenantId));
    }
    private void tenantAccess(String actor,String tenantId) {
        var viewer=user(actor,null);
        if (!actor.equals(tenantId) && !hasRole(viewer,RoleCode.ADMIN)
            && !(hasRole(viewer,RoleCode.LANDLORD) && agreements.hasConfirmedRelationship(tenantId,actor,CONFIRMED)))
            throw new AppException(UNAUTHORIZED);
        if (!users.existsById(tenantId)) throw new AppException(USER_NOT_FOUND);
    }

    @Override
    @Transactional
    public ReviewResponse reply(String actor,ReviewType type,String id,ReviewReplyRequest body) {
        user(actor,RoleCode.LANDLORD);
        if (type==ReviewType.TENANT) throw new AppException(INVALID_REQUEST);
        var entry=locked(type,id);
        if (!actor.equals(entry.getRentalAgreement().getLandlord().getId())) throw new AppException(UNAUTHORIZED);
        if (Boolean.TRUE.equals(entry.getHidden())) throw new AppException(REVIEW_HIDDEN);
        String reply=required(body.reply(),2000);
        if (entry instanceof RoomReview review) review.setLandlordReply(reply);
        else ((LandlordReview)entry).setLandlordReply(reply);
        // Flush before mapping so updatedAt in the response reflects the mutation.
        if (type==ReviewType.ROOM) rooms.flush(); else landlords.flush();
        return response(entry);
    }

    @Override
    public PageResponse<ReviewDetailResponse> admin(String actor,ReviewType type,Boolean hidden,String targetId,int page,int size) {
        user(actor,RoleCode.ADMIN);
        var pageable=pageable(page,size);
        return switch(type) {
            case ROOM -> page(rooms.findAll(filter(hidden,"room",targetId),pageable),this::detail);
            case LANDLORD -> page(landlords.findAll(filter(hidden,"landlord",targetId),pageable),this::detail);
            case TENANT -> page(tenants.findAll(filter(hidden,"tenant",targetId),pageable),this::detail);
        };
    }

    @Override
    @Transactional
    public ReviewDetailResponse visibility(String actor,ReviewType type,String id,ReviewVisibilityRequest body) {
        User admin=user(actor,RoleCode.ADMIN);
        if(body.hidden()==null) throw new AppException(INVALID_REQUEST);
        String reason=body.hidden()?required(body.reason(),500):null;
        var entry=locked(type,id);
        entry.setHidden(body.hidden());
        entry.setHiddenBy(body.hidden()?admin:null);
        entry.setHiddenAt(body.hidden()?LocalDateTime.now(clock):null);
        entry.setHiddenReason(reason);
        switch(type) {case ROOM -> rooms.flush(); case LANDLORD -> landlords.flush(); case TENANT -> tenants.flush();}
        return detail(entry);
    }

    private ReviewEntry locked(ReviewType type,String id) {
        return switch(type) {
            case ROOM -> rooms.lock(id).orElseThrow(()->new AppException(ROOM_REVIEW_NOT_FOUND));
            case LANDLORD -> landlords.lock(id).orElseThrow(()->new AppException(LANDLORD_REVIEW_NOT_FOUND));
            case TENANT -> tenants.lock(id).orElseThrow(()->new AppException(TENANT_REVIEW_NOT_FOUND));
        };
    }
    private String existing(ReviewType type,String id) {
        return switch(type) {
            case ROOM -> rooms.findByRentalAgreement_Id(id).map(RoomReview::getId).orElse(null);
            case LANDLORD -> landlords.findByRentalAgreement_Id(id).map(LandlordReview::getId).orElse(null);
            case TENANT -> tenants.findByRentalAgreement_Id(id).map(TenantReview::getId).orElse(null);
        };
    }
    private ErrorCode duplicate(ReviewType type) {
        return switch(type) {case ROOM -> ROOM_REVIEW_EXISTED;case LANDLORD -> LANDLORD_REVIEW_EXISTED;case TENANT -> TENANT_REVIEW_EXISTED;};
    }
    private boolean eligible(RentalAgreement agreement) {
        return CONFIRMED.contains(agreement.getStatus()) && agreement.getLandlordConfirmedAt()!=null
            && agreement.getTenantConfirmedAt()!=null && agreement.getStartDate()!=null
            && !agreement.getStartDate().isAfter(LocalDate.now(clock));
    }
    private void requireAuthor(String actor,ReviewType type,RentalAgreement agreement) {
        if (!actor.equals(type==ReviewType.TENANT?agreement.getLandlord().getId():agreement.getTenant().getId()))
            throw new AppException(NOT_AGREEMENT_PARTY);
        if(agreement.getTenant().getId().equals(agreement.getLandlord().getId())) throw new AppException(CANNOT_REVIEW_SELF);
    }
    private User user(String id,RoleCode role) {
        User user=users.findByIdWithRolesAndPermissions(id).orElseThrow(()->new AppException(USER_NOT_FOUND));
        if(user.getStatus()!=UserStatus.ACTIVE || (role!=null && !hasRole(user,role))) throw new AppException(UNAUTHORIZED);
        return user;
    }
    private boolean hasRole(User user,RoleCode role) {
        return user.getUserRoles().stream().anyMatch(ur->ur.getRole().getCode()==role);
    }
    private ReviewResponse response(ReviewEntry entry) {
        ReviewResponse dto;
        boolean matches;
        var agreement = entry.getRentalAgreement();
        if(entry instanceof RoomReview r) {
            dto = mapper.toResponse(r);
            matches = r.getRoom().getId().equals(agreement.getRoom().getId());
        } else if(entry instanceof LandlordReview r) {
            dto = mapper.toResponse(r);
            matches = r.getLandlord().getId().equals(agreement.getLandlord().getId());
        } else {
            var r = (TenantReview) entry;
            dto = mapper.toResponse(r);
            matches = r.getLandlord().getId().equals(agreement.getLandlord().getId());
        }
        boolean verified = matches && eligible(agreement)
                && entry.getTenant().getId().equals(agreement.getTenant().getId());
        return new ReviewResponse(dto.id(), dto.type(), dto.rating(), dto.comment(), dto.landlordReply(),
                dto.authorName(), dto.authorAvatarUrl(), dto.createdAt(), dto.updatedAt(), verified);
    }
    private ReviewDetailResponse detail(ReviewEntry entry) {
        return new ReviewDetailResponse(response(entry),entry.getRentalAgreement().getId(),Boolean.TRUE.equals(entry.getHidden()),
            entry.getHiddenBy()==null?null:entry.getHiddenBy().getId(),entry.getHiddenAt(),entry.getHiddenReason(),
            targetId(entry),targetName(entry));
    }
    private String targetId(ReviewEntry entry) {
        if(entry instanceof RoomReview review) return review.getRoom().getId();
        if(entry instanceof LandlordReview review) return review.getLandlord().getId();
        return entry.getTenant().getId();
    }
    private String targetName(ReviewEntry entry) {
        if(entry instanceof RoomReview review)
            return review.getRoom().getProperty().getName()+" - "+review.getRoom().getRoomNumber();
        if(entry instanceof LandlordReview review) return review.getLandlord().getFullName();
        return entry.getTenant().getFullName();
    }
    private static <T> Specification<T> filter(Boolean hidden,String path,String targetId) {
        return (root,query,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(hidden!=null) predicates.add(cb.equal(root.get("hidden"),hidden));
            if(targetId!=null) {
                Path<?> target=root;
                for(String part:path.split("\\.")) target=target.get(part);
                predicates.add(cb.equal(target.get("id"),targetId));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    private static Pageable pageable(int page,int size) {
        if(page<0 || size<1 || size>50) throw new AppException(INVALID_REQUEST);
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id"));
    }
    private static <T,R> PageResponse<R> page(Page<T> page,Function<T,R> map) {
        return PageResponse.<R>builder().currentPage(page.getNumber()).pageSize(page.getSize())
            .totalPages(page.getTotalPages()).totalElements(page.getTotalElements()).data(page.getContent().stream().map(map).toList()).build();
    }
    private static ReviewSummaryResponse summary(List<RatingCount> counts) {
        var distribution=new LinkedHashMap<Integer,Long>();
        for(int star=1;star<=5;star++) distribution.put(star,0L);
        long total=0,sum=0,replied=0;
        for(var row:counts) {distribution.put(row.getRating(),row.getTotal());total+=row.getTotal();sum+=row.getRating()*row.getTotal();replied+=row.getReplied()==null?0:row.getReplied();}
        return new ReviewSummaryResponse(total,total==0?null:BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP),distribution,
                total==0?null:(int)Math.round(100.0*replied/total));
    }
    private static String text(String value,int max) {
        if(value==null) return null;
        if(value.length()>max) throw new AppException(INVALID_REQUEST);
        return value.strip();
    }
    private static String required(String value,int max) {
        String result=text(value,max);
        if(result==null || result.isBlank()) throw new AppException(INVALID_REQUEST);
        return result;
    }
}
