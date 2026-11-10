package com.tamdaptit.rentalmanagement.identity.service.impl;

import com.tamdaptit.rentalmanagement.address.entity.*;
import com.tamdaptit.rentalmanagement.address.repository.NearbyPlaceRepository;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.dto.request.TenantPreferences;
import com.tamdaptit.rentalmanagement.identity.entity.*;
import com.tamdaptit.rentalmanagement.identity.repository.*;
import com.tamdaptit.rentalmanagement.identity.service.TenantPreferencesService;
import com.tamdaptit.rentalmanagement.room.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantPreferencesServiceImpl implements TenantPreferencesService {
    private final TenantProfileRepository profiles;
    private final UserRepository users;
    private final NearbyPlaceRepository places;
    @PersistenceContext
    private EntityManager em;

    public TenantPreferences get(String userId) {
        return profiles.findByUserId(userId).map(this::response).orElse(null);
    }

    @Transactional
    public TenantPreferences save(String userId, TenantPreferences r) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (r.priceMin() != null && r.priceMax() != null && r.priceMin().compareTo(r.priceMax()) > 0)
            invalid("Giá tối thiểu phải nhỏ hơn hoặc bằng giá tối đa.");
        if (r.areaMin() != null && r.areaMax() != null && r.areaMin().compareTo(r.areaMax()) > 0)
            invalid("Diện tích tối thiểu phải nhỏ hơn hoặc bằng diện tích tối đa.");
        if ((r.anchorLatitude() == null) != (r.anchorLongitude() == null))
            invalid("Cần nhập đủ kinh độ và vĩ độ.");
        var province = reference(Province.class, r.desiredProvinceId());
        var wards = ids(r.wardIds()).stream().map(id -> reference(Ward.class, id)).toList();
        for (var ward : wards) {
            if (province == null || !ward.getProvince().getId().equals(province.getId()))
                invalid("Phường/xã phải thuộc tỉnh/thành đã chọn.");
        }
        var roomType = reference(RoomType.class, r.desiredRoomTypeId());
        var required = ids(r.requiredAmenityIds());
        var amenityIds = ids(r.amenityIds());
        amenityIds.addAll(required);
        var amenities = amenityIds.stream().map(id -> reference(Amenity.class, id)).toList();
        var p = profiles.findByUserId(userId).orElseGet(() -> TenantProfile.builder().user(user).build());
        p.setTenantType(r.tenantType()); p.setOccupants(r.occupants()); p.setMoveInDate(r.moveInDate());
        p.setDesiredProvince(province); p.setDesiredWard(wards.isEmpty() ? null : wards.getFirst());
        p.setDesiredRoomType(roomType); p.setPriceMin(r.priceMin()); p.setPriceMax(r.priceMax());
        p.setAreaMin(r.areaMin()); p.setAreaMax(r.areaMax());
        p.setAnchorPlaceId(null); p.setAnchorLabel(optional(r.anchorLabel()));
        p.setAnchorLatitude(r.anchorLatitude()); p.setAnchorLongitude(r.anchorLongitude());
        if (optional(r.anchorPlaceId()) != null) {
            var place = places.findById(r.anchorPlaceId()).orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Địa điểm không tồn tại."));
            p.setAnchorPlaceId(place.getId()); p.setAnchorLabel(place.getName());
            p.setAnchorLatitude(place.getLatitude()); p.setAnchorLongitude(place.getLongitude());
        }
        if (p.getAnchorLatitude() == null) p.setAnchorLabel(null);
        p.setMaxDistanceKm(r.maxDistanceKm());
        p.setPriceTolerancePct((byte) (r.priceTolerancePct() == null ? 20 : r.priceTolerancePct().intValue()));
        p.setTopPriority(r.topPriority()); p.setNote(optional(r.note()));
        p.setAllowPersonalization(r.allowPersonalization());
        profiles.saveAndFlush(p);
        em.createQuery("delete from TenantProfileWard w where w.tenantProfile.id = :id").setParameter("id", p.getId()).executeUpdate();
        em.createQuery("delete from TenantProfileAmenity a where a.tenantProfile.id = :id").setParameter("id", p.getId()).executeUpdate();
        for (var ward : wards) em.persist(TenantProfileWard.builder().tenantProfile(p).ward(ward).build());
        for (var amenity : amenities) em.persist(TenantProfileAmenity.builder().tenantProfile(p).amenity(amenity)
                .isRequired(required.contains(amenity.getId())).build());
        em.flush();
        return response(p);
    }

    private TenantPreferences response(TenantProfile p) {
        var wards = em.createQuery("select w.ward.id from TenantProfileWard w where w.tenantProfile.id = :id order by w.ward.name", String.class)
                .setParameter("id", p.getId()).getResultList();
        if (p.getDesiredWard() != null) {
            wards = new ArrayList<>(wards);
            wards.remove(p.getDesiredWard().getId()); wards.addFirst(p.getDesiredWard().getId());
        }
        var amenities = em.createQuery("select a from TenantProfileAmenity a where a.tenantProfile.id = :id order by a.amenity.id", TenantProfileAmenity.class)
                .setParameter("id", p.getId()).getResultList();
        return new TenantPreferences(p.getTenantType(), p.getOccupants(), p.getMoveInDate(),
                p.getDesiredProvince() == null ? null : p.getDesiredProvince().getId(), wards,
                p.getDesiredRoomType() == null ? null : p.getDesiredRoomType().getId(),
                p.getPriceMin(), p.getPriceMax(), p.getAreaMin(), p.getAreaMax(),
                p.getAnchorPlaceId(), p.getAnchorLabel(), p.getAnchorLatitude(), p.getAnchorLongitude(),
                p.getMaxDistanceKm(), (int) p.getPriceTolerancePct(), p.getTopPriority(), p.getNote(), p.isAllowPersonalization(),
                amenities.stream().map(a -> a.getAmenity().getId()).toList(),
                amenities.stream().filter(TenantProfileAmenity::isRequired).map(a -> a.getAmenity().getId()).toList());
    }

    private <T> T reference(Class<T> type, String id) {
        if (optional(id) == null) return null;
        var entity = em.find(type, id);
        if (entity == null) invalid("Lựa chọn không tồn tại: " + type.getSimpleName());
        return entity;
    }
    private static LinkedHashSet<String> ids(List<String> ids) { return new LinkedHashSet<>(ids == null ? List.of() : ids); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void invalid(String message) { throw new AppException(ErrorCode.INVALID_REQUEST, message); }
}
