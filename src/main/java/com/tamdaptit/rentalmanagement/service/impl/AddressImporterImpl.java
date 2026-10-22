package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.service.*;

import com.tamdaptit.rentalmanagement.dto.address.*;
import com.tamdaptit.rentalmanagement.entity.*;
import com.tamdaptit.rentalmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AddressImporterImpl implements AddressImporter {
    private final ProvinceRepository provinces;
    private final WardRepository wards;

    @Transactional
    @Override
    public AddressSyncResult apply(AddressSnapshot snapshot) {
        Map<String, Province> provinceMap = new HashMap<>();
        for (Province p : provinces.findAll()) {
            if (provinceMap.putIfAbsent(normalize(p.getCode(), 2), p) != null) {
                throw new IllegalStateException("Duplicate province codes after normalization; resolve before sync");
            }
        }
        Map<String, Ward> wardMap = new HashMap<>();
        for (Ward w : wards.findAll()) {
            if (wardMap.putIfAbsent(normalize(w.getCode(), 5), w) != null) {
                throw new IllegalStateException("Duplicate ward codes after normalization; resolve before sync");
            }
        }
        int pc = 0, pu = 0, wc = 0, wu = 0;
        for (var data : snapshot.provinces()) {
            Province p = provinceMap.get(data.code());
            if (p == null) {
                p = provinces.save(Province.builder().code(data.code()).name(data.name()).build());
                provinceMap.put(data.code(), p);
                pc++;
            } else if (!p.getCode().equals(data.code()) || !p.getName().equals(data.name())) {
                p.setCode(data.code());
                p.setName(data.name());
                pu++;
            }
            for (var child : data.wards()) {
                Ward w = wardMap.get(child.code());
                if (w == null) {
                    w = wards.save(Ward.builder().code(child.code()).name(child.name()).province(p).build());
                    wardMap.put(child.code(), w);
                    wc++;
                } else if (!w.getCode().equals(child.code()) || !w.getName().equals(child.name())
                        || !w.getProvince().getId().equals(p.getId())) {
                    w.setCode(child.code());
                    w.setName(child.name());
                    w.setProvince(p);
                    wu++;
                }
            }
        }
        // No deletes: historical addresses may still be referenced by listings and tenants.
        wards.flush();
        return new AddressSyncResult(pc, pu, wc, wu);
    }

    private String normalize(String code, int length) {
        if (code.matches("[0-9]{1," + length + "}")) {
            return String.format(Locale.ROOT, "%0" + length + "d", Integer.parseInt(code));
        }
        return code;
    }
}
