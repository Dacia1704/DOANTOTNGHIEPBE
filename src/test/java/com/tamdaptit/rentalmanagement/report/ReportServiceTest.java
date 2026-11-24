package com.tamdaptit.rentalmanagement.report;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.Permission;
import com.tamdaptit.rentalmanagement.identity.entity.Role;
import com.tamdaptit.rentalmanagement.identity.entity.RolePermission;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.UserRole;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportCreateRequest;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportResolutionRequest;
import com.tamdaptit.rentalmanagement.report.entity.Report;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportReason;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType;
import com.tamdaptit.rentalmanagement.report.mapper.ReportMapper;
import com.tamdaptit.rentalmanagement.report.repository.ReportEvidenceRepository;
import com.tamdaptit.rentalmanagement.report.repository.ReportRepository;
import com.tamdaptit.rentalmanagement.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phần 2: Trao đổi, thông báo & Quản trị bổ trợ - ReportService")
class ReportServiceTest {

    @Mock
    private ReportRepository reports;

    @Mock
    private ReportEvidenceRepository evidences;

    @Mock
    private UserRepository users;

    @Mock
    private ListingRepository listings;

    @Mock
    private RentalAgreementRepository agreements;

    @Mock
    private NotificationPublisher notifications;

    @Mock
    private ReportMapper mapper;

    @Mock
    private JdbcTemplate jdbc;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reports, evidences, users, listings, agreements, notifications, mapper, clock, jdbc
        );
    }

    @Test
    @DisplayName("Lấy danh sách lý do báo cáo theo targetType (LISTING, LANDLORD)")
    void testReasonsByTargetType() {
        List<ReportReason> listingReasons = reportService.reasons(ReportTargetType.LISTING);
        assertThat(listingReasons).isNotEmpty();
        assertThat(listingReasons).allMatch(r -> r.supports(ReportTargetType.LISTING));

        List<ReportReason> landlordReasons = reportService.reasons(ReportTargetType.LANDLORD);
        assertThat(landlordReasons).isNotEmpty();
        assertThat(landlordReasons).allMatch(r -> r.supports(ReportTargetType.LANDLORD));

        assertThatThrownBy(() -> reportService.reasons(null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Lý do INVALID_PRICE được hỗ trợ cho LISTING nhưng không được hỗ trợ cho LANDLORD")
    void testInvalidPriceReasonConstraint() {
        assertThat(ReportReason.INVALID_PRICE.supports(ReportTargetType.LISTING)).isTrue();
        assertThat(ReportReason.INVALID_PRICE.supports(ReportTargetType.LANDLORD)).isFalse();
    }

    @Test
    @DisplayName("Tạo báo cáo với lý do không tương thích targetType bị từ chối với INVALID_REQUEST")
    void testCreateReportIncompatibleReason() {
        String actor = "tenant-1";
        User tenant = new User();
        tenant.setId(actor);
        tenant.setStatus(UserStatus.ACTIVE);

        Role role = new Role();
        role.setCode(RoleCode.TENANT);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        tenant.setUserRoles(Set.of(userRole));

        when(users.findByIdForUpdate(actor)).thenReturn(Optional.of(tenant));
        when(users.findByIdWithRolesAndPermissions(actor)).thenReturn(Optional.of(tenant));

        ReportCreateRequest body = new ReportCreateRequest(
                ReportTargetType.LANDLORD, "landlord-1", ReportReason.INVALID_PRICE, "Giá không hợp lệ"
        );

        assertThatThrownBy(() -> reportService.create(actor, body))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Người dùng không ACTIVE không được phép gửi báo cáo (UNAUTHORIZED)")
    void testInactiveUserCannotReport() {
        String actor = "inactive-tenant";
        User tenant = new User();
        tenant.setId(actor);
        tenant.setStatus(UserStatus.BLOCKED);

        Role role = new Role();
        role.setCode(RoleCode.TENANT);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        tenant.setUserRoles(Set.of(userRole));

        when(users.findByIdForUpdate(actor)).thenReturn(Optional.of(tenant));
        when(users.findByIdWithRolesAndPermissions(actor)).thenReturn(Optional.of(tenant));

        ReportCreateRequest body = new ReportCreateRequest(
                ReportTargetType.LISTING, "listing-1", ReportReason.SCAM, "Mô tả"
        );

        assertThatThrownBy(() -> reportService.create(actor, body))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private User createAdminWithPermission(String id, String permissionCode) {
        User admin = new User();
        admin.setId(id);
        admin.setStatus(UserStatus.ACTIVE);
        Permission perm = new Permission();
        perm.setCode(permissionCode);
        Role role = new Role();
        role.setCode(RoleCode.ADMIN);
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(perm);
        role.setRolePermissions(Set.of(rp));
        UserRole ur = new UserRole();
        ur.setRole(role);
        admin.setUserRoles(Set.of(ur));
        return admin;
    }

    @Test
    @DisplayName("Đóng báo cáo với kết quả outcome không phải RESOLVED hoặc REJECTED ném INVALID_REQUEST")
    void testCloseReportInvalidOutcome() {
        User admin = createAdminWithPermission("admin-1", "REPORT_HANDLE");
        when(users.findByIdWithRolesAndPermissions("admin-1")).thenReturn(Optional.of(admin));

        ReportResolutionRequest req = new ReportResolutionRequest("Ghi chú", null);

        // Outcome là PENDING không hợp lệ khi đóng
        assertThatThrownBy(() -> reportService.close("admin-1", "rep-1", ReportStatus.PENDING, req))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Đóng báo cáo không có quyền REPORT_HANDLE ném UNAUTHORIZED")
    void testCloseReportUnauthorizedWithoutPermission() {
        User normalUser = new User();
        normalUser.setId("user-no-perm");
        normalUser.setStatus(UserStatus.ACTIVE);
        normalUser.setUserRoles(Set.of());
        when(users.findByIdWithRolesAndPermissions("user-no-perm")).thenReturn(Optional.of(normalUser));

        ReportResolutionRequest req = new ReportResolutionRequest("Ghi chú", null);

        assertThatThrownBy(() -> reportService.close("user-no-perm", "rep-1", ReportStatus.RESOLVED, req))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Tiếp nhận xử lý (assign) ném REPORT_INVALID_TRANSITION nếu trạng thái báo cáo không phải PENDING")
    void testAssignReportNotPending() {
        User admin = createAdminWithPermission("admin-1", "REPORT_HANDLE");
        when(users.findByIdWithRolesAndPermissions("admin-1")).thenReturn(Optional.of(admin));

        Report report = new Report();
        report.setId("rep-1");
        report.setStatus(ReportStatus.PROCESSING); // Đã ở trạng thái đang xử lý

        when(reports.lock("rep-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.assign("admin-1", "rep-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_INVALID_TRANSITION);
    }

    @Test
    @DisplayName("Kiểm tra upload bằng chứng (checkUpload) ném IMAGE_TOO_MANY nếu đã có 5 ảnh")
    void testCheckUploadLimit() {
        User tenant = new User();
        tenant.setId("tenant-1");
        tenant.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setCode(RoleCode.TENANT);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        tenant.setUserRoles(Set.of(userRole));

        when(users.findByIdWithRolesAndPermissions("tenant-1")).thenReturn(Optional.of(tenant));

        Report report = new Report();
        report.setId("rep-1");
        report.setStatus(ReportStatus.PENDING);
        report.setTenant(tenant);

        when(reports.findById("rep-1")).thenReturn(Optional.of(report));
        when(evidences.countByReport_Id("rep-1")).thenReturn(5L);

        assertThatThrownBy(() -> reportService.checkUpload("tenant-1", "rep-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_TOO_MANY);
    }

    @Test
    @DisplayName("Kiểm tra upload bằng chứng (checkUpload) ném UNAUTHORIZED nếu không phải chủ báo cáo")
    void testCheckUploadUnauthorizedForOtherTenant() {
        User tenant = new User();
        tenant.setId("tenant-other");
        tenant.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setCode(RoleCode.TENANT);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        tenant.setUserRoles(Set.of(userRole));

        User owner = new User();
        owner.setId("tenant-owner");

        when(users.findByIdWithRolesAndPermissions("tenant-other")).thenReturn(Optional.of(tenant));

        Report report = new Report();
        report.setId("rep-1");
        report.setStatus(ReportStatus.PENDING);
        report.setTenant(owner);

        when(reports.findById("rep-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.checkUpload("tenant-other", "rep-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Kiểm tra upload bằng chứng (checkUpload) ném REPORT_NOT_PENDING nếu báo cáo đã xử lý")
    void testCheckUploadNotPending() {
        User tenant = new User();
        tenant.setId("tenant-1");
        tenant.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setCode(RoleCode.TENANT);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        tenant.setUserRoles(Set.of(userRole));

        when(users.findByIdWithRolesAndPermissions("tenant-1")).thenReturn(Optional.of(tenant));

        Report report = new Report();
        report.setId("rep-1");
        report.setStatus(ReportStatus.RESOLVED);
        report.setTenant(tenant);

        when(reports.findById("rep-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.checkUpload("tenant-1", "rep-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_PENDING);
    }
}
