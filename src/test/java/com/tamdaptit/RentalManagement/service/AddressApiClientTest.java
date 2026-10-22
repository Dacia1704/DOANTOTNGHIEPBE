package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.service.impl.AddressApiClientImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tamdaptit.rentalmanagement.exception.AppException;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AddressApiClientTest {
    private final AddressApiClientImpl client = new AddressApiClientImpl(new ObjectMapper(), "http://localhost");
    private static final String DATA = """
            [{"code":1,"name":"Thành phố Hà Nội","wards":[{"code":4,"name":"Phường Ba Đình"}]}]
            """;

    @Test void parsesCodesAndVietnameseNames() throws Exception {
        var province = client.parse(DATA).provinces().getFirst();
        assertEquals("01", province.code());
        assertEquals("00004", province.wards().getFirst().code());
        assertEquals("Phường Ba Đình", province.wards().getFirst().name());
    }

    @Test void rejectsEmptyIncompleteAndDuplicateSnapshots() {
        for (String json : new String[]{"[]", "{}", "null", "[{\"code\":1,\"name\":\"X\"}]",
                "[{\"code\":1,\"name\":\"X\",\"wards\":[]}]",
                DATA.replace("\"code\":4", "\"code\":-1"),
                DATA.replace("\"code\":1", "\"code\":1.5"),
                "[" + DATA.substring(1, DATA.length() - 2) + "," + DATA.substring(1)}) {
            assertThrows(Exception.class, () -> client.parse(json));
        }
    }

    @Test void fetchesUtf8OverHttpAndRejectsHttpErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = DATA.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var fetched = new AddressApiClientImpl(new ObjectMapper(), url + "/ok").fetch();
            assertEquals("Thành phố Hà Nội", fetched.provinces().getFirst().name());
            assertThrows(AppException.class, () -> new AddressApiClientImpl(new ObjectMapper(), url + "/error").fetch());
        } finally { server.stop(0); }
    }
}
