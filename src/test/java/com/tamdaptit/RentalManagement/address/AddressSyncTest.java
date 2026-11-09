package com.tamdaptit.rentalmanagement.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot.ProvinceData;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot.WardData;
import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.address.repository.ProvinceRepository;
import com.tamdaptit.rentalmanagement.address.repository.WardRepository;
import com.tamdaptit.rentalmanagement.address.service.impl.AddressApiClientImpl;
import com.tamdaptit.rentalmanagement.address.service.impl.AddressImporterImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressSyncTest {
    @Test void fetchesEveryPageAndKeepsAdministrativeTypeAndCentroids() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.endsWith("/new-provinces")) {
                body = """
                    {"success":true,"data":[{"code":"01","name":"Hà Nội","type":"Thành phố",
                    "centroid_lat":21.02,"centroid_lng":105.83,"ward_count":2}],"metadata":{"total":1}}
                    """;
            } else {
                String code = exchange.getRequestURI().getQuery().contains("page=1") ? "00166" : "00160";
                body = """
                    {"success":true,"data":[{"code":"%s","name":"Cầu Giấy","type":"Phường",
                    "province_code":"01","centroid_lat":21.03,"centroid_lng":105.79}],"metadata":{"total":2}}
                    """.formatted(code);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new AddressApiClientImpl(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", 0);
            var snapshot = client.fetch();
            assertEquals(1, snapshot.provinces().size());
            var province = snapshot.provinces().getFirst();
            assertEquals("Thành phố Hà Nội", province.name());
            assertEquals(2, province.wards().size());
            assertEquals("Phường Cầu Giấy", province.wards().getFirst().name());
            assertEquals(0, province.wards().getFirst().centroidLng().compareTo(new BigDecimal("105.79")));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsInvalidOrHalfCoordinatePairsAndDuplicateCodes() {
        var client = new AddressApiClientImpl(new ObjectMapper(), "http://localhost", 0);
        for (String pair : List.of("\"centroid_lat\":21", "\"centroid_lat\":91,\"centroid_lng\":105",
                "\"centroid_lat\":\"bad\",\"centroid_lng\":105")) {
            String json = "[{\"code\":\"01\",\"name\":\"Province\",\"wards\":[{\"code\":\"00166\",\"name\":\"Ward\","
                    + pair + "}]}]";
            assertThrows(IllegalStateException.class, () -> client.parse(json));
        }
        String duplicate = """
            [{"code":"01","name":"P","wards":[{"code":"00166","name":"W"},{"code":"00166","name":"Other"}]}]
            """;
        assertThrows(IllegalStateException.class, () -> client.parse(duplicate));
    }

    @Test void importPreservesIdsIsIdempotentAndDoesNotEraseCoordinatesWhenSourceHasNone() {
        var provinces = mock(ProvinceRepository.class);
        var wards = mock(WardRepository.class);
        var province = Province.builder().id("province-id").code("01").name("Hà Nội").build();
        var ward = Ward.builder().id("ward-id").code("00166").name("Cầu Giấy").province(province).build();
        when(provinces.findAll()).thenReturn(List.of(province));
        when(wards.findAll()).thenReturn(List.of(ward));
        var importer = new AddressImporterImpl(provinces, wards);
        var snapshot = new AddressSnapshot(List.of(new ProvinceData("01", "Hà Nội",
                new BigDecimal("21.02000000"), new BigDecimal("105.83000000"),
                List.of(new WardData("00166", "Cầu Giấy", new BigDecimal("21.03"), new BigDecimal("105.79"))))));
        var first = importer.apply(snapshot);
        assertEquals(1, first.provincesUpdated());
        assertEquals(1, first.wardsUpdated());
        assertEquals("ward-id", ward.getId());
        assertEquals("province-id", province.getId());
        assertEquals(0, importer.apply(snapshot).wardsUpdated());
        importer.apply(new AddressSnapshot(List.of(new ProvinceData("01", "Hà Nội", null, null,
                List.of(new WardData("00166", "Cầu Giấy", null, null))))));
        assertNotNull(ward.getCentroidLat());
        verify(wards, never()).delete(any(Ward.class));
    }
}
