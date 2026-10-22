package com.tamdaptit.rentalmanagement.address.service.impl;

import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSyncResult;
import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.address.repository.ProvinceRepository;
import com.tamdaptit.rentalmanagement.address.repository.WardRepository;
import com.tamdaptit.rentalmanagement.address.service.AddressImporter;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dong bo du lieu don vi hanh chinh tu snapshot cua nguon ben ngoai.
 */
@Service
@RequiredArgsConstructor
public class AddressImporterImpl implements AddressImporter {

    private final ProvinceRepository provinces;
    private final WardRepository wards;

    @Override
    @Transactional
    public AddressSyncResult apply(AddressSnapshot snapshot) {
        Map<String, Province> provinceMap = new HashMap<>();
        for (Province p : provinces.findAll()) {
            if (provinceMap.putIfAbsent(normalize(p.getCode(), 2), p) != null) {
                throw new AppException(
                        ErrorCode.ADDRESS_CODE_EXISTS,
                        "Tồn tại mã tỉnh/thành phố trùng lặp sau khi chuẩn hóa, vui lòng xử lý trước khi đồng bộ");
            }
        }

        Map<String, Ward> wardMap = new HashMap<>();
        for (Ward w : wards.findAll()) {
            if (wardMap.putIfAbsent(normalize(w.getCode(), 5), w) != null) {
                throw new AppException(
                        ErrorCode.ADDRESS_CODE_EXISTS,
                        "Tồn tại mã phường/xã trùng lặp sau khi chuẩn hóa, vui lòng xử lý trước khi đồng bộ");
            }
        }

        int provincesCreated = 0;
        int provincesUpdated = 0;
        int wardsCreated = 0;
        int wardsUpdated = 0;

        for (var data : snapshot.provinces()) {
            Province province = provinceMap.get(data.code());
            if (province == null) {
                province = provinces.save(
                        Province.builder().code(data.code()).name(data.name()).build());
                provinceMap.put(data.code(), province);
                provincesCreated++;
            } else if (!province.getCode().equals(data.code())
                    || !province.getName().equals(data.name())) {
                province.setCode(data.code());
                province.setName(data.name());
                provincesUpdated++;
            }

            for (var child : data.wards()) {
                Ward ward = wardMap.get(child.code());
                if (ward == null) {
                    wards.save(Ward.builder()
                            .code(child.code())
                            .name(child.name())
                            .province(province)
                            .build());
                    wardMap.put(child.code(), ward);
                    wardsCreated++;
                } else if (!ward.getCode().equals(child.code())
                        || !ward.getName().equals(child.name())
                        || !ward.getProvince().getId().equals(province.getId())) {
                    ward.setCode(child.code());
                    ward.setName(child.name());
                    ward.setProvince(province);
                    wardsUpdated++;
                }
            }
        }

        // Khong xoa don vi bi go: du lieu lich su van duoc tham chieu boi tin dang va ho so.
        wards.flush();
        return new AddressSyncResult(provincesCreated, provincesUpdated, wardsCreated, wardsUpdated);
    }

    private String normalize(String code, int length) {
        if (code.matches("[0-9]{1," + length + "}")) {
            return String.format(Locale.ROOT, "%0" + length + "d", Integer.parseInt(code));
        }
        return code;
    }
}
