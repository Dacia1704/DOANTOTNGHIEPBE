package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.service.AddressImporter;

import com.tamdaptit.rentalmanagement.dto.address.*;
import com.tamdaptit.rentalmanagement.dto.address.AddressSnapshot.*;
import com.tamdaptit.rentalmanagement.entity.*;
import com.tamdaptit.rentalmanagement.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressImporterTest {
    @Test void updatesExistingCodesWithoutChangingIdsAndCanRepeat() {
        var provinces = mock(ProvinceRepository.class);
        var wards = mock(WardRepository.class);
        var p = Province.builder().id("province-id").code("1").name("Old province").build();
        var w = Ward.builder().id("ward-id").code("4").name("Old ward").province(p).build();
        when(provinces.findAll()).thenReturn(List.of(p));
        when(wards.findAll()).thenReturn(List.of(w));
        var importer = new AddressImporter(provinces, wards);
        var snapshot = new AddressSnapshot(List.of(new ProvinceData("01", "Hà Nội",
                List.of(new WardData("00004", "Ba Đình")))));
        assertEquals(new AddressSyncResult(0, 1, 0, 1), importer.apply(snapshot));
        assertEquals(new AddressSyncResult(0, 0, 0, 0), importer.apply(snapshot));
        assertEquals("province-id", p.getId());
        assertEquals("ward-id", w.getId());
        assertEquals("00004", w.getCode());
        verify(wards, never()).deleteAll();
        verify(wards, never()).save(any());
    }

    @Test void refusesAmbiguousExistingCodes() {
        var provinces = mock(ProvinceRepository.class);
        var wards = mock(WardRepository.class);
        when(provinces.findAll()).thenReturn(List.of(
                Province.builder().code("1").build(), Province.builder().code("01").build()));
        assertThrows(IllegalStateException.class,
                () -> new AddressImporter(provinces, wards).apply(new AddressSnapshot(List.of())));
        verifyNoInteractions(wards);
    }
}
