package com.tamdaptit.rentalmanagement.rental.service.impl;

import com.tamdaptit.rentalmanagement.rental.service.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.rental.dto.request.*;
import com.tamdaptit.rentalmanagement.rental.dto.response.*;
import com.tamdaptit.rentalmanagement.rental.entity.*;
import com.tamdaptit.rentalmanagement.rental.entity.enums.*;
import com.tamdaptit.rentalmanagement.rental.repository.*;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;
import static com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus.*;
import static com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus.PENDING_CONFIRMATION;
import static com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus.ACTIVE;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class RentalServiceImpl implements RentalService {
    private final RoomRepository rooms;
    private final ListingRepository listings;
    private final RentalRequestRepository requests;
    private final RentalAgreementRepository agreements;
    private final UserRepository users;
    private final com.tamdaptit.rentalmanagement.property.repository.PropertyImageRepository propertyImages;
    private final RentalAvailabilityService availability;
    private final RentalEvents events;
    private final ObjectMapper json;
    private final Clock clock;
    @Value("${app.rental.contract-terms:Hai bên thanh toán đúng kỳ; bảo quản tài sản; trao đổi và thống nhất việc bàn giao, chấm dứt. Mẫu tham khảo, cần rà soát điều khoản trước khi sử dụng thực tế.}")
    private String terms;

    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).truncatedTo(java.time.temporal.ChronoUnit.SECONDS); }
    public static LocalDate endDate(LocalDate start,int months) { return start.plusMonths(months).minusDays(1); }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalRequestResponse create(String userId,RentalRequestCreateRequest body) {
        User tenant=active(userId);
        Room room=lockRoom(body.roomId());
        Listing listing=validListing(body.listingId(),room);
        if(listing.getProperty().getLandlord().getId().equals(userId)) throw new AppException(CANNOT_REQUEST_OWN_LISTING);
        validateBooking(body,room);
        var pending=requests.pendingForRoom(room.getId()); // current reads under the room lock
        for(var r:pending) {
            if(!r.getExpiresAt().isAfter(now())) resolve(r,EXPIRED,"Hết hạn yêu cầu",userId);
            else if(r.getTenant().getId().equals(userId)) {
                if(same(r,body)) return requestResponse(r,true,null);
                throw new AppException(REQUEST_ALREADY_PENDING);
            }
        }
        availability.requireFree(room);
        requests.flush(); // release expired generated unique keys before inserting a replacement
        RentalRequest r=new RentalRequest();
        r.setRoom(room); r.setListing(listing); r.setTenant(tenant); r.setLandlord(listing.getProperty().getLandlord());
        r.setTenantFullName(body.tenantFullName().trim()); r.setTenantPhone(body.tenantPhone().trim());
        r.setTenantEmail(body.tenantEmail().trim()); r.setTenantCitizenId(body.tenantCitizenId());
        r.setTenantPermanentAddress(body.tenantPermanentAddress().trim()); r.setOccupantCount(body.occupantCount());
        r.setDurationMonths(body.durationMonths()); r.setProposedMoveInDate(body.proposedMoveInDate());
        r.setMessage(normalize(body.message())); r.setExpiresAt(now().plusDays(7));
        requests.saveAndFlush(r);
        events.emit(userId,r.getLandlord().getId(),r.getId(),"RENTAL_REQUEST","REQUEST_CREATED",null);
        return requestResponse(r,true,null);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalAgreementResponse accept(String userId,String id) {
        active(userId);
        Room room=lockRoom(requests.roomId(id).orElseThrow(()->new AppException(RENTAL_REQUEST_NOT_FOUND)));
        RentalRequest r=requests.lock(id).orElseThrow(()->new AppException(RENTAL_REQUEST_NOT_FOUND));
        requireOwner(userId,r.getLandlord().getId());
        requireOwner(userId,room.getProperty().getLandlord().getId());
        if(r.getStatus()==ACCEPTED) return agreementResponse(agreements.findByRentalRequest_Id(id)
                .orElseThrow(()->new AppException(INVALID_AGREEMENT_STATUS)),userId);
        requirePending(r);
        validListing(r.getListing().getId(),room);
        if(r.getProposedMoveInDate()==null || r.getProposedMoveInDate().isBefore(now().toLocalDate())
                || r.getDurationMonths()==null || r.getTenantCitizenId()==null || r.getOccupantCount()==null
                || room.getMaxOccupants()==null || r.getOccupantCount()>room.getMaxOccupants())
            throw new AppException(INVALID_REQUEST,"Yêu cầu đã cũ hoặc ngày/số người không còn hợp lệ, hãy gửi lại");
        active(r.getTenant().getId());
        availability.requireFree(room);
        checkMoney(room.getPrice(),true); checkMoney(room.getDefaultDeposit(),false);
        checkMoney(room.getElectricityPrice(),false); checkMoney(room.getWaterPrice(),false); checkMoney(room.getServiceFee(),false);
        LocalDateTime time=now();
        r.setStatus(ACCEPTED); r.setRespondedAt(time);
        RentalAgreement a=new RentalAgreement();
        a.setAgreementCode("HD-"+UUID.randomUUID()); a.setRentalRequest(r); a.setRoom(room);
        a.setTenant(r.getTenant()); a.setLandlord(r.getLandlord()); a.setPrice(room.getPrice());
        a.setDeposit(room.getDefaultDeposit()); a.setElectricityPrice(room.getElectricityPrice());
        a.setWaterPrice(room.getWaterPrice()); a.setServiceFee(room.getServiceFee());
        a.setStartDate(r.getProposedMoveInDate()); a.setEndDate(endDate(a.getStartDate(),r.getDurationMonths()));
        a.setStatus(PENDING_CONFIRMATION); a.setCreatedByRole(CreatedByRole.LANDLORD);
        a.setLandlordConfirmedAt(time); a.setConfirmationExpiresAt(time.plusHours(48)); a.setTermsText(terms);
        a.setContractSnapshot(snapshot(a,r));
        agreements.saveAndFlush(a);
        for(var other:requests.pendingForRoom(room.getId()))
            if(!other.getId().equals(id)) resolve(other,REJECTED,"Chủ trọ đã chọn yêu cầu khác cho phòng này",userId);
        events.emit(userId,r.getTenant().getId(),a.getId(),"RENTAL_AGREEMENT","REQUEST_ACCEPTED",null);
        return agreementResponse(a,userId);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalRequestResponse cancelRequest(String userId,String id,boolean landlord,String reason) {
        lockRoom(requests.roomId(id).orElseThrow(()->new AppException(RENTAL_REQUEST_NOT_FOUND)));
        var r=requests.lock(id).orElseThrow(()->new AppException(RENTAL_REQUEST_NOT_FOUND));
        requireOwner(userId,landlord?r.getLandlord().getId():r.getTenant().getId());
        requirePending(r); resolve(r,landlord?REJECTED:CANCELLED,reason,userId);
        return requestResponse(r,true,null);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalAgreementResponse confirm(String userId,String id,RentalAgreementConfirmRequest body) {
        active(userId);
        var a=lockAgreement(id); requireOwner(userId,a.getTenant().getId());
        if(!Boolean.TRUE.equals(body.accepted())) throw new AppException(INVALID_REQUEST);
        if(a.getStatus()==ACTIVE) {
            if(!Objects.equals(body.expectedPdfSha256(),a.getPdfSha256())) throw new AppException(CONTRACT_CHANGED);
            return agreementResponse(a,userId);
        }
        requireConfirmable(a);
        if(a.getPdfStatus()!=ContractPdfStatus.READY || a.getPdfSha256()==null) throw new AppException(CONTRACT_PDF_NOT_READY);
        if(!a.getPdfSha256().equals(body.expectedPdfSha256())) throw new AppException(CONTRACT_CHANGED);
        if(a.getRoom().getRentalStatus()!=RoomRentalStatus.AVAILABLE) throw new AppException(ROOM_UNAVAILABLE);
        a.setStatus(ACTIVE); a.setTenantConfirmedAt(now()); a.getRoom().setRentalStatus(RoomRentalStatus.RENTED);
        events.emit(userId,a.getLandlord().getId(),id,"RENTAL_AGREEMENT","AGREEMENT_ACTIVE",a.getPdfSha256());
        return agreementResponse(a,userId);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalAgreementResponse decline(String userId,String id) {
        var a=lockAgreement(id); requireOwner(userId,a.getTenant().getId());
        requireConfirmable(a); release(a,"Người thuê từ chối hợp đồng",userId,"AGREEMENT_DECLINED");
        return agreementResponse(a,userId);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public RentalAgreementResponse checkout(String userId,String id,RentalCheckoutRequest body) {
        var a=lockAgreement(id); requireOwner(userId,a.getLandlord().getId());
        if(a.getStatus()!=ACTIVE || !Boolean.TRUE.equals(body.handoverConfirmed())) throw new AppException(INVALID_AGREEMENT_STATUS);
        a.setStatus(now().toLocalDate().isAfter(a.getEndDate())?RentalAgreementStatus.EXPIRED:RentalAgreementStatus.TERMINATED);
        a.setCancelReason(body.reason());
        if(a.getRoom().getRentalStatus()!=RoomRentalStatus.MAINTENANCE) a.getRoom().setRentalStatus(RoomRentalStatus.AVAILABLE);
        events.emit(userId,a.getTenant().getId(),id,"RENTAL_AGREEMENT","CHECKOUT",a.getPdfSha256());
        return agreementResponse(a,userId);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public void expireRequest(String id) {
        var roomId=requests.roomId(id); // nullable legacy records are not new bookings
        if(roomId.isPresent()) lockRoom(roomId.get());
        var r=requests.lock(id).orElse(null);
        if(r!=null && r.getStatus()==PENDING && r.getExpiresAt()!=null && !r.getExpiresAt().isAfter(now()))
            resolve(r,EXPIRED,"Hết hạn yêu cầu",null);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public void expireAgreement(String id) {
        var a=lockAgreement(id);
        if(a.getStatus()==PENDING_CONFIRMATION && a.getConfirmationExpiresAt()!=null && !a.getConfirmationExpiresAt().isAfter(now()))
            release(a,"Hết thời hạn xác nhận",null,"CONFIRMATION_TIMEOUT");
    }
    private void release(RentalAgreement a,String reason,String actor,String event) {
        a.setStatus(RentalAgreementStatus.CANCELLED); a.setCancelReason(reason);
        // A pending hold never changed rental_status. Preserve MAINTENANCE and any legacy state.
        events.emit(actor,a.getLandlord().getId(),a.getId(),"RENTAL_AGREEMENT",event,a.getPdfSha256());
        events.emit(actor,a.getTenant().getId(),a.getId(),"RENTAL_AGREEMENT",event,a.getPdfSha256());
    }
    @Override
    public RentalRequestResponse request(String userId,String id,boolean admin) {
        var r=requests.findById(id).orElseThrow(()->new AppException(RENTAL_REQUEST_NOT_FOUND));
        party(userId,r.getTenant().getId(),r.getLandlord().getId(),admin);
        return requestResponse(r,true,agreements.findByRentalRequest_Id(id).orElse(null));
    }
    @Override
    public RentalAgreementResponse agreement(String userId,String id,boolean admin) {
        var a=agreements.findById(id).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND));
        party(userId,a.getTenant().getId(),a.getLandlord().getId(),admin); return agreementResponse(a,userId);
    }
    @Override
    public PdfReference pdf(String userId,String id,boolean admin) {
        var a=agreements.findById(id).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND));
        party(userId,a.getTenant().getId(),a.getLandlord().getId(),admin);
        if(a.getPdfStatus()==ContractPdfStatus.FAILED) throw new AppException(PDF_GENERATION_FAILED);
        if(a.getPdfStatus()!=ContractPdfStatus.READY || a.getPdfPublicId()==null) throw new AppException(CONTRACT_PDF_NOT_READY);
        return new PdfReference(a.getPdfPublicId(),a.getPdfSha256(),a.getAgreementCode());
    }
    @Override
    public PageResponse<RentalRequestResponse> requests(String user,boolean owner,int page,int size,RentalRequestStatus status,String property,String room) {
        var p=requests.search(user,owner,status,property,room,pageable(page,size));
        return requestPage(p);
    }
    @Override
    public PageResponse<RentalRequestResponse> myRequests(String user,int page,int size,RentalRequestStatus status,String q,String sort) {
        return requestPage(requests.searchMine(user,status,searchTerm(q),tenantPageable(page,size,sort)));
    }
    private PageResponse<RentalRequestResponse> requestPage(Page<RentalRequest> p) {
        var ids=p.getContent().stream().map(RentalRequest::getId).toList();
        var contracts=ids.isEmpty()?Map.<String,RentalAgreement>of():agreements.findAllByRentalRequest_IdIn(ids).stream()
            .collect(java.util.stream.Collectors.toMap(a->a.getRentalRequest().getId(),Function.identity()));
        Map<String,String> thumbnails=new HashMap<>();
        if(!p.isEmpty()) propertyImages.findAllByProperty_IdInOrderBySortOrderAsc(p.getContent().stream()
            .map(r->r.getListing().getProperty().getId()).distinct().toList())
            .forEach(image->thumbnails.putIfAbsent(image.getProperty().getId(),image.getUrl()));
        return page(p,r->requestResponse(r,false,contracts.get(r.getId()),thumbnails.get(r.getListing().getProperty().getId())));
    }
    @Override
    public PageResponse<RentalAgreementResponse> agreements(String user,boolean owner,int page,int size) {
        return agreementPage(agreements.search(user,owner,pageable(page,size)),user);
    }
    @Override
    public PageResponse<RentalAgreementResponse> myAgreements(String user,int page,int size,String q,String sort) {
        return agreementPage(agreements.searchMine(user,searchTerm(q),tenantPageable(page,size,sort)),user);
    }
    private PageResponse<RentalAgreementResponse> agreementPage(Page<RentalAgreement> p,String user) {
        Map<String,String> thumbnails = new HashMap<>();
        if (!p.isEmpty()) propertyImages.findAllByProperty_IdInOrderBySortOrderAsc(p.getContent().stream()
                .map(a->a.getRoom().getProperty().getId()).distinct().toList())
                .forEach(image->thumbnails.putIfAbsent(image.getProperty().getId(),image.getUrl()));
        return page(p,a->agreementResponse(a,user,thumbnails.get(a.getRoom().getProperty().getId())));
    }
    @Override
    public RentalSummaryResponse mySummary(String user) {
        Map<String,Long> statuses = new LinkedHashMap<>();
        for (var status : RentalRequestStatus.values()) statuses.put(status.name(),0L);
        requests.countStatuses(user).forEach(row->statuses.put(row.getStatus().name(),row.getTotal()));
        return new RentalSummaryResponse(statuses.values().stream().mapToLong(Long::longValue).sum(),statuses,agreements.countByTenant_Id(user));
    }
    private String searchTerm(String q) {
        if (q == null) return "";
        if (q.length()>100) throw new AppException(INVALID_REQUEST,"Từ khóa tối đa 100 ký tự");
        return q.trim();
    }
    private Pageable tenantPageable(int p,int s,String sort) {
        var defaults = pageable(p,s);
        if ("newest".equals(sort)) return defaults;
        if ("oldest".equals(sort)) return PageRequest.of(p,s,Sort.by("createdAt").ascending().and(Sort.by("id")));
        throw new AppException(INVALID_REQUEST,"Thứ tự sắp xếp không hợp lệ");
    }
    private <T,R> PageResponse<R> page(Page<T> p,Function<T,R> mapper) {
        return PageResponse.<R>builder().currentPage(p.getNumber()).pageSize(p.getSize()).totalElements(p.getTotalElements())
            .totalPages(p.getTotalPages()).data(p.getContent().stream().map(mapper).toList()).build();
    }
    private Pageable pageable(int p,int s) {
        if(p<0 || s<1 || s>100) throw new AppException(INVALID_REQUEST);
        return PageRequest.of(p,s,Sort.by("createdAt").descending().and(Sort.by("id")));
    }
    private RentalRequestResponse requestResponse(RentalRequest r,boolean detail,RentalAgreement a) {
        String thumbnail = detail ? propertyImages.findAllByProperty_IdInOrderBySortOrderAsc(
                List.of(r.getListing().getProperty().getId())).stream().findFirst().map(image -> image.getUrl()).orElse(null) : null;
        return requestResponse(r,detail,a,thumbnail);
    }
    private RentalRequestResponse requestResponse(RentalRequest r,boolean detail,RentalAgreement a,String thumbnail) {
        return new RentalRequestResponse(r.getId(),r.getListing().getId(),r.getRoom()==null?null:r.getRoom().getId(),
            r.getRoom()==null?null:r.getRoom().getRoomNumber(),r.getListing().getProperty().getId(),r.getListing().getProperty().getName(),
            r.getTenantFullName(),detail?r.getTenantPhone():null,detail?r.getTenantEmail():null,
            detail&&r.getTenantCitizenId()!=null?"********"+r.getTenantCitizenId().substring(r.getTenantCitizenId().length()-4):null,
            detail?r.getTenantPermanentAddress():null,r.getProposedMoveInDate(),r.getProposedMoveInDate()!=null&&r.getDurationMonths()!=null?endDate(r.getProposedMoveInDate(),r.getDurationMonths()):null,
            r.getDurationMonths(),r.getOccupantCount(),detail?r.getMessage():null,r.getStatus().name(),r.getResolutionReason(),
            r.getCreatedAt(),r.getExpiresAt(),a==null?null:a.getId(),a==null?null:a.getPdfStatus().name(),thumbnail,
            detail ? requestRoomResponse(r,a) : null,
            rentalCard(r.getListing().getProperty(),r.getRoom(),r.getLandlord().getFullName(),
                a!=null?a.getPrice():r.getRoom()==null?null:r.getRoom().getPrice(),r.getListing().getTitle(),thumbnail));
    }
    private RentalCardResponse rentalCard(com.tamdaptit.rentalmanagement.property.entity.Property p,Room room,String landlord,BigDecimal price,String title,String thumbnail) {
        String address=java.util.stream.Stream.of(p.getAddressLine(),p.getWard()==null?null:p.getWard().getName(),
                p.getProvince()==null?null:p.getProvince().getName()).filter(Objects::nonNull)
                .filter(value->!value.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        return new RentalCardResponse(thumbnail,address,room==null?null:room.getRoomType().getName(),
                room==null?null:room.getAreaM2(),landlord,price,title);
    }
    private RentalRequestRoomResponse requestRoomResponse(RentalRequest r,RentalAgreement a) {
        var p = r.getListing().getProperty(); var room = r.getRoom();
        String address = java.util.stream.Stream.of(p.getAddressLine(), p.getWard()==null?null:p.getWard().getName(),
                p.getProvince()==null?null:p.getProvince().getName()).filter(Objects::nonNull)
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        return new RentalRequestRoomResponse(r.getListing().getTitle(), r.getLandlord().getFullName(), p.getRentalMode().name(),
                room==null?null:room.getRoomType().getName(), room==null?null:room.getFloorNumber(),
                room==null?null:room.getAreaM2(), room==null?null:room.getMaxOccupants(), address, p.getLatitude(), p.getLongitude(),
                a!=null?a.getPrice():room==null?null:room.getPrice(),
                a!=null?a.getDeposit():room==null?null:room.getDefaultDeposit(),
                a!=null?a.getElectricityPrice():room==null?null:room.getElectricityPrice(),
                a!=null?a.getWaterPrice():room==null?null:room.getWaterPrice(),
                a!=null?a.getServiceFee():room==null?null:room.getServiceFee(), a!=null?"AGREEMENT":"CURRENT_ROOM");
    }
    private RentalAgreementResponse agreementResponse(RentalAgreement a,String user) {
        return agreementResponse(a,user,null);
    }
    private RentalAgreementResponse agreementResponse(RentalAgreement a,String user,String thumbnail) {
        var s=readSnapshot(a.getContractSnapshot());
        boolean pending=a.getStatus()==PENDING_CONFIRMATION && a.getConfirmationExpiresAt()!=null && a.getConfirmationExpiresAt().isAfter(now());
        boolean tenant=a.getTenant().getId().equals(user),ready=a.getPdfStatus()==ContractPdfStatus.READY;
        return new RentalAgreementResponse(a.getId(),a.getRentalRequest()==null?null:a.getRentalRequest().getId(),a.getAgreementCode(),a.getRoom().getId(),
            s.getOrDefault("roomNumber",a.getRoom().getRoomNumber()),s.getOrDefault("propertyName",a.getRoom().getProperty().getName()),
            a.getTenant().getId(),s.getOrDefault("tenantName",a.getTenant().getFullName()),
            a.getLandlord().getId(),s.getOrDefault("landlordName",a.getLandlord().getFullName()),
            a.getPrice(),a.getDeposit(),a.getElectricityPrice(),a.getWaterPrice(),a.getServiceFee(),a.getStartDate(),a.getEndDate(),a.getStatus().name(),
            a.getPdfStatus().name(),ready?a.getPdfSha256():null,a.getConfirmationExpiresAt(),a.getLandlordConfirmedAt(),a.getTenantConfirmedAt(),
            tenant&&pending&&ready,tenant&&pending,ready,a.getLandlord().getId().equals(user)&&a.getStatus()==ACTIVE,a.getCancelReason(),
            rentalCard(a.getRoom().getProperty(),a.getRoom(),s.getOrDefault("landlordName",a.getLandlord().getFullName()),a.getPrice(),null,thumbnail));
    }
    private String snapshot(RentalAgreement a,RentalRequest r) {
        Map<String,String> s=new LinkedHashMap<>(); var p=a.getRoom().getProperty(); var room=a.getRoom(); var l=a.getLandlord();
        s.put("schemaVersion","2"); s.put("agreementCode",a.getAgreementCode()); s.put("issuedAt",now().toString());
        s.put("landlordName",value(l.getFullName())); s.put("landlordPhone",value(l.getPhone())); s.put("landlordEmail",value(l.getEmail()));
        s.put("landlordCitizenId",value(l.getCitizenId()));
        s.put("landlordAddress","Chưa cung cấp"); s.put("tenantName",r.getTenantFullName()); s.put("tenantPhone",r.getTenantPhone());
        s.put("tenantEmail",r.getTenantEmail()); s.put("tenantCitizenId",r.getTenantCitizenId()); s.put("tenantAddress",r.getTenantPermanentAddress());
        s.put("propertyName",p.getName()); s.put("address",p.getAddressLine()+", "+p.getWard().getName()+", "+p.getProvince().getName());
        s.put("roomNumber",room.getRoomNumber()); s.put("roomType",room.getRoomType().getName()); s.put("areaM2",room.getAreaM2().toPlainString());
        s.put("maxOccupants",String.valueOf(room.getMaxOccupants())); s.put("occupantCount",String.valueOf(r.getOccupantCount()));
        s.put("price",money(a.getPrice())); s.put("deposit",money(a.getDeposit())); s.put("electricityPrice",money(a.getElectricityPrice()));
        s.put("waterPrice",money(a.getWaterPrice())); s.put("serviceFee",money(a.getServiceFee())); s.put("feeUnits","Đơn vị điện/nước: chưa cung cấp; hai bên cần thống nhất.");
        s.put("paymentCycleMonths","1"); s.put("durationMonths",String.valueOf(r.getDurationMonths())); s.put("startDate",a.getStartDate().toString());
        s.put("endDate",a.getEndDate().toString()); s.put("terms",a.getTermsText()); s.put("landlordConfirmedAt",a.getLandlordConfirmedAt().toString());
        s.put("tenantConfirmation","Chờ người thuê xác nhận trong ứng dụng; xem trạng thái xác nhận hiện tại trong ứng dụng.");
        try { return json.writeValueAsString(s); } catch(Exception ex) { throw new IllegalStateException("Cannot serialize contract snapshot"); }
    }
    @Override
    public Map<String,String> readSnapshot(String snapshot) {
        if(snapshot==null) return Map.of();
        try { return json.readValue(snapshot,new TypeReference<Map<String,String>>(){}); }
        catch(Exception ex) { throw new IllegalStateException("Invalid stored contract snapshot"); }
    }
    private static String value(String v) { return v==null||v.isBlank()?"Chưa cung cấp":v; }
    private static String money(BigDecimal v) { return v==null?"Chưa cung cấp":v.toPlainString()+" VND"; }
    private void checkMoney(BigDecimal v,boolean required) { if((required&&v==null)||(v!=null&&(v.signum()<0||(required&&v.signum()==0)))) throw new AppException(INVALID_REQUEST,"Giá/phí của phòng không hợp lệ"); }
    private User active(String id) { var u=users.findById(id).orElseThrow(()->new AppException(USER_NOT_FOUND)); if(u.getStatus()!=UserStatus.ACTIVE) throw new AppException(USER_BLOCKED); return u; }
    private Room lockRoom(String id) { return rooms.findByIdForUpdate(id).orElseThrow(()->new AppException(ROOM_NOT_FOUND)); }
    private RentalAgreement lockAgreement(String id) { lockRoom(agreements.roomId(id).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND))); return agreements.lock(id).orElseThrow(()->new AppException(RENTAL_AGREEMENT_NOT_FOUND)); }
    private void requireOwner(String actual,String expected) { if(!Objects.equals(actual,expected)) throw new AppException(UNAUTHORIZED); }
    private void party(String user,String tenant,String landlord,boolean admin) { if(!admin&&!user.equals(tenant)&&!user.equals(landlord)) throw new AppException(NOT_AGREEMENT_PARTY); }
    private void requirePending(RentalRequest r) { if(r.getStatus()!=PENDING) throw new AppException(REQUEST_NOT_PENDING); if(r.getExpiresAt()==null||!r.getExpiresAt().isAfter(now())) throw new AppException(RENTAL_REQUEST_EXPIRED); }
    private void requireConfirmable(RentalAgreement a) { if(a.getStatus()!=PENDING_CONFIRMATION) throw new AppException(INVALID_AGREEMENT_STATUS); if(a.getConfirmationExpiresAt()==null||!a.getConfirmationExpiresAt().isAfter(now())) throw new AppException(CONTRACT_CONFIRMATION_EXPIRED); }
    private Listing validListing(String id,Room room) {
        var l=listings.findById(id).orElseThrow(()->new AppException(LISTING_NOT_FOUND));
        if(l.getProperty().getLandlord().getStatus()!=com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus.ACTIVE || l.getStatus()!=ListingStatus.PUBLISHED || l.getExpiresAt()==null || !l.getExpiresAt().isAfter(now())) throw new AppException(LISTING_NOT_FOUND);
        if(!l.getProperty().getId().equals(room.getProperty().getId())) throw new AppException(INVALID_REQUEST,"Phòng không thuộc cơ sở của tin đăng");
        if(l.getProperty().getRentalMode()==PropertyRentalMode.WHOLE && rooms.countByProperty_Id(l.getProperty().getId())!=1)
            throw new AppException(INVALID_REQUEST,"Cơ sở nguyên căn chưa có mapping một phòng đại diện hợp lệ");
        return l;
    }
    private void validateBooking(RentalRequestCreateRequest b,Room room) {
        if(b.tenantFullName().trim().length()<2 || b.tenantPermanentAddress().trim().length()<5)
            throw new AppException(INVALID_REQUEST,"Họ tên hoặc địa chỉ quá ngắn");
        if(!Boolean.TRUE.equals(b.acceptedPrivacyNotice()) || b.proposedMoveInDate()==null || b.proposedMoveInDate().isBefore(now().toLocalDate())
            || b.durationMonths()==null || b.durationMonths()<1 || b.durationMonths()>36 || b.occupantCount()==null || b.occupantCount()<1
            || room.getMaxOccupants()==null || b.occupantCount()>room.getMaxOccupants()) throw new AppException(INVALID_REQUEST,"Ngày thuê, kỳ hạn, số người hoặc xác nhận riêng tư không hợp lệ");
    }
    private String normalize(String v) { return v==null?"":v.trim(); }
    private boolean same(RentalRequest r,RentalRequestCreateRequest b) {
        return r.getListing().getId().equals(b.listingId()) && r.getTenantFullName().equals(b.tenantFullName().trim())
            && r.getTenantPhone().equals(b.tenantPhone().trim()) && r.getTenantEmail().equals(b.tenantEmail().trim())
            && r.getTenantCitizenId().equals(b.tenantCitizenId()) && r.getTenantPermanentAddress().equals(b.tenantPermanentAddress().trim())
            && r.getDurationMonths().equals(b.durationMonths()) && r.getOccupantCount().equals(b.occupantCount())
            && r.getProposedMoveInDate().equals(b.proposedMoveInDate()) && normalize(r.getMessage()).equals(normalize(b.message()));
    }
    private void resolve(RentalRequest r,RentalRequestStatus status,String reason,String actor) {
        r.setStatus(status); r.setRespondedAt(now()); r.setResolutionReason(reason);
        events.emit(actor,status==CANCELLED?r.getLandlord().getId():r.getTenant().getId(),r.getId(),"RENTAL_REQUEST","REQUEST_"+status,null);
    }
}
