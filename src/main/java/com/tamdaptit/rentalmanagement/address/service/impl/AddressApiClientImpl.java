package com.tamdaptit.rentalmanagement.address.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot.ProvinceData;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot.WardData;
import com.tamdaptit.rentalmanagement.address.service.AddressService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Fetch a complete paginated snapshot before the importer opens its transaction. */
@Component
public class AddressApiClientImpl implements AddressService {
    private final String source;
    private final ObjectMapper mapper;
    private final long requestIntervalMs;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AddressApiClientImpl(ObjectMapper mapper,
            @Value("${app.address-sync.url:https://www.tinhthanhpho.com/api/v1}") String url,
            @Value("${app.address-sync.request-interval-ms:650}") long requestIntervalMs) {
        this.mapper = mapper;
        this.source = url.replaceAll("/+$", "");
        this.requestIntervalMs = Math.max(0, requestIntervalMs);
    }

    @Override
    public AddressSnapshot fetch() {
        try {
            ArrayNode provinces = fetchPages("/new-provinces");
            for (JsonNode province : provinces) {
                String provinceCode = code(province, 2);
                ArrayNode wards = fetchPages("/new-provinces/" + provinceCode + "/wards");
                if (province.has("ward_count")
                        && province.path("ward_count").asInt(-1) != wards.size()) throw invalid();
                for (JsonNode ward : wards) {
                    if (!provinceCode.equals(code(ward.path("province_code"), 2))) throw invalid();
                }
                ((ObjectNode) province).set("wards", wards);
            }
            return parse(mapper.writeValueAsString(provinces));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.ADDRESS_SYNC_FAILED);
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            throw new AppException(ErrorCode.ADDRESS_SYNC_FAILED);
        }
    }

    private ArrayNode fetchPages(String path) throws IOException, InterruptedException {
        ArrayNode rows = mapper.createArrayNode();
        int total = -1;
        for (int page = 1; page <= 100; page++) {
            // The public lookup API allows 100 requests/minute/IP.
            Thread.sleep(requestIntervalMs);
            URI uri = URI.create(source + path + "?limit=100&page=" + page);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("User-Agent", "RentalManagement-AdministrativeSync/1.0")
                    .GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw invalid();
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.path("success").asBoolean()
                    || !root.path("data").isArray()
                    || !root.path("metadata").path("total").canConvertToInt()) throw invalid();
            int expected = root.path("metadata").path("total").intValue();
            if (expected <= 0 || (total >= 0 && total != expected)) throw invalid();
            total = expected;
            JsonNode data = root.path("data");
            if (data.isEmpty()) throw invalid();
            rows.addAll((ArrayNode) data);
            if (rows.size() == total) return rows;
            if (rows.size() > total) throw invalid();
        }
        throw invalid();
    }

    /** Validates the assembled snapshot, including every coordinate pair, before writes. */
    @Override
    public AddressSnapshot parse(String json) throws IOException {
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
                var coordinates = coordinates(w);
                wards.add(new WardData(wardCode, name(w), coordinates[0], coordinates[1]));
            }
            var coordinates = coordinates(p);
            provinces.add(new ProvinceData(code, name(p), coordinates[0], coordinates[1], List.copyOf(wards)));
        }
        return new AddressSnapshot(List.copyOf(provinces));
    }

    private String code(JsonNode node, int length) {
        String value = node.isValueNode() ? node.asText() : node.path("code").asText();
        if (!value.matches("[0-9]{1," + length + "}")) throw invalid();
        int number = Integer.parseInt(value);
        if (number <= 0) throw invalid();
        return String.format(Locale.ROOT, "%0" + length + "d", number);
    }

    private String name(JsonNode node) {
        if (!node.path("name").isTextual()) throw invalid();
        String name = node.path("name").asText().trim();
        if (name.isEmpty()) throw invalid();
        String type = node.path("type").asText("").trim();
        if (!type.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(type.toLowerCase(Locale.ROOT) + " ")) {
            name = type + " " + name;
        }
        if (name.isEmpty() || name.length() > 100) throw invalid();
        return name;
    }

    private BigDecimal[] coordinates(JsonNode node) {
        JsonNode lat = node.path("centroid_lat"), lng = node.path("centroid_lng");
        boolean hasLat = !lat.isMissingNode() && !lat.isNull();
        boolean hasLng = !lng.isMissingNode() && !lng.isNull();
        if (!hasLat && !hasLng) return new BigDecimal[]{null, null};
        if (!hasLat || !hasLng || !lat.isNumber() || !lng.isNumber()) throw invalid();
        BigDecimal latitude = lat.decimalValue(), longitude = lng.decimalValue();
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) throw invalid();
        return new BigDecimal[]{latitude, longitude};
    }

    private IllegalStateException invalid() {
        return new IllegalStateException("Dữ liệu đơn vị hành chính không hợp lệ hoặc thiếu");
    }
}
