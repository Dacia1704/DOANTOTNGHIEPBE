package com.tamdaptit.rentalmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamdaptit.rentalmanagement.dto.address.AddressSnapshot;
import com.tamdaptit.rentalmanagement.dto.address.AddressSnapshot.*;
import com.tamdaptit.rentalmanagement.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class AddressApiClient {
    private final URI source;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AddressApiClient(ObjectMapper mapper,
            @Value("${app.address-sync.url:https://provinces.open-api.vn/api/v2/?depth=2}") String url) {
        this.mapper = mapper;
        this.source = URI.create(url);
    }

    public AddressSnapshot fetch() {
        try {
            var request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalArgumentException("Upstream status " + response.statusCode());
            return parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.ADDRESS_SYNC_FAILED);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ADDRESS_SYNC_FAILED);
        }
    }

    // Validate the complete snapshot before starting any database transaction.
    public AddressSnapshot parse(String json) throws java.io.IOException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isArray() || root.isEmpty()) throw invalid();
        var provinces = new ArrayList<ProvinceData>();
        var provinceCodes = new HashSet<String>();
        var wardCodes = new HashSet<String>();
        for (JsonNode p : root) {
            String code = code(p, 2);
            if (!provinceCodes.add(code)) throw invalid();
            JsonNode children = p.path("wards");
            if (!children.isArray() || children.isEmpty()) throw invalid();
            var wards = new ArrayList<WardData>();
            for (JsonNode w : children) {
                String wardCode = code(w, 5);
                if (!wardCodes.add(wardCode)) throw invalid();
                wards.add(new WardData(wardCode, name(w)));
            }
            provinces.add(new ProvinceData(code, name(p), List.copyOf(wards)));
        }
        return new AddressSnapshot(List.copyOf(provinces));
    }

    private String code(JsonNode node, int length) {
        String value = node.path("code").asText();
        if (!value.matches("[0-9]{1," + length + "}")) throw invalid();
        int number = Integer.parseInt(value);
        if (number <= 0) throw invalid();
        return String.format(Locale.ROOT, "%0" + length + "d", number);
    }

    private String name(JsonNode node) {
        if (!node.path("name").isTextual()) throw invalid();
        String name = node.path("name").asText().trim();
        if (name.isEmpty() || name.length() > 100) throw invalid();
        return name;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or incomplete administrative snapshot");
    }
}
