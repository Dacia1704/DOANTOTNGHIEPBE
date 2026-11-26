package com.tamdaptit.rentalmanagement.discovery;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.tamdaptit.rentalmanagement.discovery.FeedRanking.Item;
import com.tamdaptit.rentalmanagement.discovery.FeedRanking.Preferences;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phần 3: Tìm kiếm & Gợi ý phòng trọ - FeedRanking Listing & Discovery")
class FeedRankingListingTest {
    private final Instant fixedNow = Instant.parse("2026-11-20T10:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    @DisplayName("Chuẩn hóa tiếng Việt bỏ dấu và ký tự đặc biệt")
    void testNormalize() {
        assertThat(FeedRanking.normalize("Phường Dịch Vọng Hậu, Cầu Giấy!"))
            .isEqualTo("phuong dich vong hau cau giay");
        assertThat(FeedRanking.normalize("Hà Nội #phongtro_2026"))
            .isEqualTo("ha noi #phongtro_2026");
        assertThat(FeedRanking.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("Chấm điểm vị trí (location score) đối chiếu hồ sơ tìm kiếm")
    void testLocationScoring() {
        // Hồ sơ tenant muốn tìm ở phường Cầu Giấy (wardId = "ward-cg", provinceId = "hn")
        Preferences prefs = new Preferences(
            "hn",
            "Hà Nội",
            List.of("ward-cg"),
            List.of("Phường Cầu Giấy"),
            null,
            null,
            null,
            null,
            "",
            true
        );

        // Khớp đúng phường mong muốn -> điểm 1.0
        assertThat(FeedRanking.location(prefs, "hn", "ward-cg", "Đường Cầu Giấy")).isEqualTo(1.0);

        // Chỉ đúng tỉnh, khác phường -> điểm 0.4
        assertThat(FeedRanking.location(prefs, "hn", "ward-khac", "Đường Hà Đông")).isEqualTo(0.4);

        // Khác cả tỉnh và phường -> điểm 0.0
        assertThat(FeedRanking.location(prefs, "hcm", "ward-q1", "Quận 1")).isZero();

        // Khi người dùng tắt cá nhân hóa (disabled) -> điểm trung lập 0.5
        Preferences disabled = Preferences.empty();
        assertThat(FeedRanking.location(disabled, "hn", "ward-cg", "")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Thuật toán xen kẽ vị trí 5/3/2 (interleave) phân bổ theo mức ưu tiên")
    void testInterleaveSlotAllocation() {
        List<Item> items = new ArrayList<>();
        // Tạo các tin với 3 mức độ ưu tiên: 2 (VIP cao), 1 (Boost thấp), 0 (Thường)
        for (int i = 1; i <= 6; i++) {
            items.add(new Item("vip-" + i, "owner-vip-" + i, 2, 0.90 - i * 0.01, 0.8, 10));
            items.add(new Item("boost-" + i, "owner-boost-" + i, 1, 0.70 - i * 0.01, 0.8, 10));
            items.add(new Item("normal-" + i, "owner-normal-" + i, 0, 0.50 - i * 0.01, 0.8, 10));
        }

        List<String> ranking = FeedRanking.interleave(items, true);

        // Template 10 vị trí: [2, 1, 2, 0, 2, 1, 2, 0, 2, 1]
        // Vị trí 0: VIP (tier 2)
        assertThat(ranking.get(0)).startsWith("vip-");
        // Vị trí 1: Boost (tier 1)
        assertThat(ranking.get(1)).startsWith("boost-");
        // Vị trí 2: VIP (tier 2)
        assertThat(ranking.get(2)).startsWith("vip-");
        // Vị trí 3: Thường (tier 0)
        assertThat(ranking.get(3)).startsWith("normal-");
        // Vị trí 4: VIP (tier 2)
        assertThat(ranking.get(4)).startsWith("vip-");
        // Vị trí 5: Boost (tier 1)
        assertThat(ranking.get(5)).startsWith("boost-");
    }

    @Test
    @DisplayName("Ràng buộc đa dạng hóa: tối đa 2 tin từ cùng một chủ trọ trong mỗi chu kỳ 10 vị trí")
    void testLandlordDiversityConstraint() {
        List<Item> items = new ArrayList<>();
        // Chủ nhà "greedy-landlord" có 5 tin VIP điểm rất cao
        for (int i = 1; i <= 5; i++) {
            items.add(new Item("greedy-" + i, "greedy-landlord", 2, 0.99 - i * 0.001, 0.9, 5));
        }

        // Các chủ nhà khác có tin VIP điểm thấp hơn
        for (int i = 1; i <= 10; i++) {
            items.add(new Item("other-" + i, "other-landlord-" + i, 2, 0.80 - i * 0.01, 0.8, 10));
            items.add(new Item("boost-" + i, "boost-landlord-" + i, 1, 0.70 - i * 0.01, 0.8, 10));
            items.add(new Item("normal-" + i, "normal-landlord-" + i, 0, 0.50 - i * 0.01, 0.8, 10));
        }

        List<String> ranking = FeedRanking.interleave(items, true);

        // Trong 10 vị trí đầu tiên, "greedy-landlord" chỉ được xuất hiện tối đa 2 lần
        long greedyCountInFirst10 = ranking.subList(0, 10)
            .stream()
            .filter(id -> id.startsWith("greedy-"))
            .count();

        assertThat(greedyCountInFirst10).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Vị trí thứ 10 là lượt khám phá (Discovery slot) ưu tiên tin mới ít lượt hiển thị")
    void testDiscoverySlotSelection() {
        List<Item> items = new ArrayList<>();
        // Tạo các tin VIP, Boost, Thường lấp đầy các vị trí trước
        for (int i = 1; i <= 5; i++) {
            items.add(new Item("vip-" + i, "owner-v" + i, 2, 0.9, 0.5, 500));
            items.add(new Item("boost-" + i, "owner-b" + i, 1, 0.7, 0.5, 300));
            items.add(new Item("normal-" + i, "owner-n" + i, 0, 0.5, 0.5, 200));
        }

        // Thêm một tin mới (fresh cao = 1.0) và chưa ai xem (exposure = 0)
        Item newDiscoveryItem = new Item("fresh-discovery", "owner-fresh", 1, 0.3, 1.0, 0);
        items.add(newDiscoveryItem);

        List<String> ranking = FeedRanking.interleave(items, true);

        // Vị trí thứ 10 (index 9) được tính theo 0.6 * fresh + 0.4 / (1 + exposure)
        // Tin fresh-discovery sẽ được chọn vào slot khám phá này dù điểm score gốc thấp hơn
        assertThat(ranking.get(9)).isEqualTo("fresh-discovery");
    }

    @Test
    @DisplayName("Lọc phòng khớp tiêu chí giá và trạng thái còn trống (matchingRooms)")
    void testMatchingRooms_PriceAndAvailability() {
        RoomResponse availableRoom = createRoom(
            "r1",
            new BigDecimal("2500000"),
            new BigDecimal("25"),
            RoomRentalStatus.AVAILABLE,
            "AVAILABLE",
            2,
            1,
            List.of()
        );
        RoomResponse occupiedRoom = createRoom(
            "r2",
            new BigDecimal("2000000"),
            new BigDecimal("20"),
            RoomRentalStatus.RENTED,
            "RENTED",
            1,
            1,
            List.of()
        );
        RoomResponse expensiveRoom = createRoom(
            "r3",
            new BigDecimal("4500000"),
            new BigDecimal("35"),
            RoomRentalStatus.AVAILABLE,
            "AVAILABLE",
            2,
            2,
            List.of()
        );

        ListingResponse listing = createListing(
            "l1",
            "prop1",
            List.of(availableRoom, occupiedRoom, expensiveRoom),
            List.of()
        );

        // Lọc phòng trống có giá từ 2.000.000 đến 3.000.000
        ListingSearchRequest searchReq = new ListingSearchRequest(
            null,
            null,
            null,
            null,
            new BigDecimal("2000000"),
            new BigDecimal("3000000"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<RoomResponse> matched = FeedRanking.matchingRooms(listing, searchReq, true);

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).id()).isEqualTo("r1");
    }

    @Test
    @DisplayName("Lọc phòng khớp tiêu chí diện tích, số người ở và tiện ích")
    void testMatchingRooms_AreaOccupantsAndAmenities() {
        RoomResponse.AmenityResponse wifi = new RoomResponse.AmenityResponse("a1", "WiFi", "wifi-icon");
        RoomResponse.AmenityResponse ac = new RoomResponse.AmenityResponse("a2", "Điều hòa", "ac-icon");

        RoomResponse smallRoom = createRoom(
            "r1",
            new BigDecimal("2000000"),
            new BigDecimal("15"),
            RoomRentalStatus.AVAILABLE,
            "AVAILABLE",
            1,
            1,
            List.of(wifi)
        );
        RoomResponse spaciousRoomWithAc = createRoom(
            "r2",
            new BigDecimal("3500000"),
            new BigDecimal("30"),
            RoomRentalStatus.AVAILABLE,
            "AVAILABLE",
            3,
            2,
            List.of(wifi, ac)
        );

        ListingResponse listing = createListing("l2", "prop2", List.of(smallRoom, spaciousRoomWithAc), List.of());

        // Tìm phòng diện tích >= 20m2, ở được >= 2 người, có Điều hòa
        ListingSearchRequest searchReq = new ListingSearchRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("Điều hòa"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("20"),
            new BigDecimal("40"),
            2,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<RoomResponse> matched = FeedRanking.matchingRooms(listing, searchReq, false);

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).id()).isEqualTo("r2");
    }

    @Test
    @DisplayName("Khớp tin đăng theo từ khóa tìm kiếm (matchesListing)")
    void testMatchesListing_KeywordSearch() {
        ListingResponse listing = createListing("l3", "prop3", List.of(), List.of());

        // Tìm kiếm đúng từ khóa trong tiêu đề
        ListingSearchRequest keywordMatch = createSearchWithKeyword("Phòng trọ cao cấp");
        assertThat(FeedRanking.matchesListing(listing, keywordMatch)).isTrue();

        // Tìm kiếm không dấu
        ListingSearchRequest unaccentedMatch = createSearchWithKeyword("phong tro cao cap");
        assertThat(FeedRanking.matchesListing(listing, unaccentedMatch)).isTrue();

        // Tìm kiếm từ khóa không có trong tin
        ListingSearchRequest mismatch = createSearchWithKeyword("Chung cư Royal City");
        assertThat(FeedRanking.matchesListing(listing, mismatch)).isFalse();
    }

    @Test
    @DisplayName("Trích xuất hashtags từ nội dung bài viết cộng đồng")
    void testExtractTags() {
        String postContent = "Cần tìm trọ gấp #phongtro #caugiay giá dưới 3tr #HaNoi!";
        Set<String> tags = FeedRanking.tags(postContent);

        assertThat(tags).containsExactlyInAnyOrder("#phongtro", "#caugiay", "#hanoi");
    }

    @Test
    @DisplayName("Tính điểm tươi mới giảm dần theo thời gian (freshness decay)")
    void testFreshnessDecay() {
        FeedRanking feedRanking = new FeedRanking(null, clock);

        LocalDateTime now = LocalDateTime.ofInstant(fixedNow, ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime oneDayAgo = now.minusDays(1);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        double freshNow = feedRanking.freshness(now, 30 * 24);
        double fresh1Day = feedRanking.freshness(oneDayAgo, 30 * 24);
        double fresh30Days = feedRanking.freshness(thirtyDaysAgo, 30 * 24);

        assertThat(freshNow).isEqualTo(1.0);
        assertThat(fresh1Day).isLessThan(freshNow).isGreaterThan(fresh30Days);
        assertThat(fresh30Days).isCloseTo(Math.exp(-1.0), Offset.offset(0.01));
    }

    @Test
    @DisplayName("Chấm điểm bài viết cộng đồng kết hợp độ quan tâm, tương tác và theo dõi")
    void testCommunityScoreCalculation() {
        // Double communityScore(interest, engagement, following, fresh, trust, seen)
        double scoreFollowing = FeedRanking.communityScore(0.8, 0.6, true, 0.9, 0.7, 0);
        double scoreNotFollowing = FeedRanking.communityScore(0.8, 0.6, false, 0.9, 0.7, 0);

        // Khi người dùng follow tác giả, điểm được cộng thêm trọng số 0.20
        assertThat(scoreFollowing).isGreaterThan(scoreNotFollowing);

        // Khi bài viết đã xem nhiều lần (seen cao), điểm bị suy giảm theo mẫu số 1 + 0.25 * seen
        double scoreSeenFrequently = FeedRanking.communityScore(0.8, 0.6, true, 0.9, 0.7, 10);
        assertThat(scoreSeenFrequently).isLessThan(scoreFollowing);
    }

    // Helper builders
    private RoomResponse createRoom(
        String id,
        BigDecimal price,
        BigDecimal area,
        RoomRentalStatus rentalStatus,
        String bookingAvailability,
        int maxOccupants,
        int floorNumber,
        List<RoomResponse.AmenityResponse> amenities
    ) {
        return new RoomResponse(
            id,
            "prop1",
            "101",
            floorNumber,
            "landlord1",
            "type1",
            "Phòng trọ khép kín",
            price,
            new BigDecimal("3500"),
            new BigDecimal("25000"),
            BigDecimal.ZERO,
            price,
            area,
            maxOccupants,
            "hn",
            "Hà Nội",
            "ward1",
            "Dịch Vọng",
            "123 Cầu Giấy",
            new BigDecimal("21.03"),
            new BigDecimal("105.78"),
            rentalStatus,
            bookingAvailability,
            amenities,
            List.of(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private ListingResponse createListing(
        String id,
        String propertyId,
        List<RoomResponse> rooms,
        List<RoomResponse.AmenityResponse> propAmenities
    ) {
        PropertyResponse property = new PropertyResponse(
            propertyId,
            "landlord1",
            "Nhà trọ Thăng Long",
            "ptype1",
            "Nhà riêng",
            PropertyRentalMode.BY_ROOM,
            5,
            "hn",
            "Hà Nội",
            "ward1",
            "Dịch Vọng",
            "123 Cầu Giấy",
            new BigDecimal("21.03"),
            new BigDecimal("105.78"),
            propAmenities,
            List.of(),
            rooms,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        return new ListingResponse(
            id,
            propertyId,
            "landlord1",
            "Phòng trọ cao cấp Dịch Vọng Cầu Giấy",
            "phong-tro-cao-cap",
            "Phòng trọ đầy đủ tiện nghi, có điều hòa, nóng lạnh, an ninh tốt, gần đại học Quốc Gia",
            ListingStatus.PUBLISHED,
            null,
            1,
            1,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now(),
            property,
            "Nhà trọ Thăng Long",
            PropertyRentalMode.BY_ROOM,
            "Nguyễn Văn Landlord",
            false,
            null
        );
    }

    private ListingSearchRequest createSearchWithKeyword(String keyword) {
        return new ListingSearchRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            keyword,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
