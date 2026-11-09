package com.tamdaptit.rentalmanagement.listing;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPriceStatisticsResponse;
import java.util.List;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.impl.ListingServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingMapServiceTest {
    @Mock ListingRepository repository;
    @Mock Clock subscriptionClock;
    @InjectMocks ListingServiceImpl service;

    @Test
    void searchesPublishedMapWithoutTheListPaginationLimit() {
        when(subscriptionClock.instant()).thenReturn(Instant.parse("2026-11-09T00:00:00Z"));
        when(subscriptionClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(repository.searchPublished(any(), any(), any(), any(), any(), any(), any(),
                anyList(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        assertTrue(service.searchPublishedMap(request(null, null)).isEmpty());
        var invocation = mockingDetails(repository).getInvocations().iterator().next();
        var arguments = invocation.getArguments();
        assertTrue(((Pageable) arguments[arguments.length - 1]).isUnpaged());
    }

    @Test
    void mapSearchRetainsPriceValidation() {
        assertThrows(AppException.class, () -> service.searchPublishedMap(
                request(BigDecimal.TEN, BigDecimal.ONE)));
        verifyNoInteractions(repository);
    }

    @Test
    void groupsAllAreasByProvinceAndSelectedProvinceByWard() {
        when(subscriptionClock.instant()).thenReturn(Instant.parse("2026-11-09T00:00:00Z"));
        when(subscriptionClock.getZone()).thenReturn(ZoneOffset.UTC);
        var provinces = List.of(new ListingPriceStatisticsResponse("province", "Tỉnh A", 2500000d, 20L));
        var wards = List.of(new ListingPriceStatisticsResponse("ward", "Phường A", 3000000d, 5L));
        when(repository.priceStatisticsByProvince(any())).thenReturn(provinces);
        when(repository.priceStatisticsByWard(any(), eq("province"), eq("ward"))).thenReturn(wards);
        assertEquals(provinces, service.priceStatistics(null, null));
        assertEquals(wards, service.priceStatistics("province", "ward"));
        verify(repository).priceStatisticsByWard(any(), eq("province"), eq("ward"));
    }

    @Test
    void rejectsWardStatisticsWithoutItsProvince() {
        assertThrows(AppException.class, () -> service.priceStatistics(null, "ward"));
        verifyNoInteractions(repository);
    }

    private ListingSearchRequest request(BigDecimal min, BigDecimal max) {
        return new ListingSearchRequest(null, null, null, null, min, max, null,
                null, null, null, null, null, null, null);
    }
}
