package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.service.WardService;

import com.tamdaptit.rentalmanagement.dto.address.WardRequest;
import com.tamdaptit.rentalmanagement.entity.*;
import com.tamdaptit.rentalmanagement.exception.*;
import com.tamdaptit.rentalmanagement.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WardServiceTest {
    private final WardRepository wards = mock(WardRepository.class);
    private final ProvinceRepository provinces = mock(ProvinceRepository.class);
    private final WardService service = new WardService(wards, provinces);

    @Test void refusesDeleteWhenWardIsReferenced() {
        when(wards.findById("w")).thenReturn(Optional.of(Ward.builder().id("w").build()));
        when(wards.countReferences("w")).thenReturn(1);
        var ex = assertThrows(AppException.class, () -> service.delete("w"));
        assertEquals(ErrorCode.ADDRESS_IN_USE, ex.getErrorCode());
        verify(wards, never()).delete(any(Ward.class));
    }

    @Test void rejectsDuplicateCodeAndUnknownProvince() {
        var request = new WardRequest("00001", "Ward", "p");
        when(wards.existsByCodeAndIdNot("00001", "")).thenReturn(true);
        assertEquals(ErrorCode.ADDRESS_CODE_EXISTS,
                assertThrows(AppException.class, () -> service.create(request)).getErrorCode());
        when(wards.existsByCodeAndIdNot("00001", "")).thenReturn(false);
        when(provinces.findById("p")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.PROVINCE_NOT_FOUND,
                assertThrows(AppException.class, () -> service.create(request)).getErrorCode());
        verify(wards, never()).saveAndFlush(any());
    }

    @Test void rejectsInvalidPaginationBeforeQueryingDatabase() {
        assertThrows(AppException.class, () -> service.list(null, null, -1, 20));
        assertThrows(AppException.class, () -> service.list(null, null, 0, 101));
        verifyNoInteractions(wards, provinces);
    }
}
