package vn.QL_KyTucXa.Config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.QL_KyTucXa.Entity.Permission;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.RolePermission;
import vn.QL_KyTucXa.Entity.RolePermissionId;
import vn.QL_KyTucXa.Repository.Permission_Repository;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Khởi tạo (idempotent) danh mục quyền và gán quyền cho 4 vai trò ADMIN /
 * MANAGER / STAFF / STUDENT
 * theo ma trận phân quyền của hệ thống. ADMIN luôn được gán toàn bộ quyền.
 * Chỉ bổ sung quyền còn thiếu, không xóa các gán quyền do quản trị viên chỉnh
 * tay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RbacSeeder {

    private static final String MANAGER = "MANAGER";
    private static final String STAFF = "STAFF";
    private static final String STUDENT = "STUDENT";

    @Autowired
    Permission_Repository permissionRepository;

    @Autowired
    RolePermission_Repository rolePermissionRepository;

    /** mã quyền, phân hệ, mô tả, các vai trò (ngoài ADMIN) được cấp quyền này */
    private record Def(String code, String module, String description, List<String> roles) {
    }

    private static Def def(String code, String module, String description, String... roles) {
        return new Def(code, module, description, List.of(roles));
    }

    private static final List<Def> DEFINITIONS = List.of(
            // 1. Hệ thống, bảo mật, RBAC
            def("RBAC_VIEW", "SYSTEM", "Xem danh sách vai trò và quyền hạn", MANAGER),
            def("RBAC_MANAGE", "SYSTEM", "Tạo/sửa/xóa vai trò, quyền và gán quyền"),
            def("USER_MANAGE", "SYSTEM", "CRUD tài khoản người dùng, đổi vai trò"),
            def("USER_LOCK", "SYSTEM", "Khóa/mở khóa tài khoản", MANAGER),
            def("AUDIT_VIEW", "SYSTEM", "Xem nhật ký thao tác hệ thống"),
            def("SETTING_VIEW", "SYSTEM", "Xem cấu hình hệ thống", MANAGER),
            def("SYSTEM_CONFIG", "SYSTEM", "Sửa cấu hình hệ thống"),
            // 2. Hồ sơ
            def("STUDENT_VIEW", "PROFILE", "Xem/tìm sinh viên", MANAGER, STAFF),
            def("STUDENT_MANAGE", "PROFILE", "Tạo/sửa/xóa sinh viên", MANAGER),
            def("STAFF_VIEW", "PROFILE", "Xem nhân viên", MANAGER),
            def("STAFF_MANAGE", "PROFILE", "Tạo/sửa nhân viên", MANAGER),
            // 3. Cơ sở vật chất
            def("ROOM_VIEW", "FACILITY", "Xem tòa, tầng, loại phòng, phòng, giường", MANAGER, STAFF, STUDENT),
            def("ROOM_MANAGE", "FACILITY", "Thêm/sửa/xóa tòa, tầng, loại phòng, phòng, giường", MANAGER),
            def("ROOM_STATUS", "FACILITY", "Đổi trạng thái phòng/giường", MANAGER, STAFF),
            def("ASSET_VIEW", "FACILITY", "Xem tài sản và tài sản trong phòng", MANAGER, STAFF),
            def("ASSET_MANAGE", "FACILITY", "Thêm/sửa/xóa tài sản", MANAGER),
            // 4. Đăng ký và hợp đồng
            def("CONTRACT_VIEW", "CONTRACT", "Xem toàn bộ hợp đồng, chuyển phòng", MANAGER, STAFF),
            def("CONTRACT_DRAFT", "CONTRACT", "Soạn/sửa hợp đồng nháp, tạo yêu cầu chuyển phòng", MANAGER, STAFF),
            def("CONTRACT_MANAGE", "CONTRACT", "Duyệt/chấm dứt/xóa hợp đồng, duyệt chuyển phòng, quản lý đợt đăng ký",
                    MANAGER),
            // 5. Dịch vụ tiện ích
            def("UTILITY_VIEW", "UTILITY", "Xem chỉ số điện nước, đăng ký xe", MANAGER, STAFF),
            def("UTILITY_MANAGE", "UTILITY", "Nhập chỉ số điện nước", MANAGER, STAFF),
            def("CATALOG_MANAGE", "UTILITY", "Quản lý loại dịch vụ, đơn giá, loại vi phạm", MANAGER),
            // 6. Hóa đơn và thanh toán
            def("INVOICE_VIEW", "INVOICE", "Xem toàn bộ hóa đơn", MANAGER, STAFF),
            def("INVOICE_DRAFT", "INVOICE", "Lập/sửa hóa đơn nháp", MANAGER, STAFF),
            def("INVOICE_MANAGE", "INVOICE", "Phát hành, hủy, giảm trừ, xóa hóa đơn", MANAGER),
            def("PAYMENT_VIEW", "INVOICE", "Xem toàn bộ thanh toán", MANAGER, STAFF),
            def("PAYMENT_MANAGE", "INVOICE", "Ghi nhận thu tiền", MANAGER, STAFF),
            // 7. Vi phạm
            def("VIOLATION_VIEW", "VIOLATION", "Xem toàn bộ vi phạm", MANAGER, STAFF),
            def("VIOLATION_MANAGE", "VIOLATION", "Lập biên bản, cập nhật xử lý vi phạm", MANAGER, STAFF),
            // 8. Bảo trì, thông báo, phản ánh
            def("MAINTENANCE_CREATE", "MAINTENANCE", "Gửi yêu cầu bảo trì", MANAGER, STUDENT),
            def("MAINTENANCE_VIEW", "MAINTENANCE", "Xem toàn bộ yêu cầu bảo trì", MANAGER, STAFF),
            def("MAINTENANCE_MANAGE", "MAINTENANCE", "Phân công, cập nhật tiến độ bảo trì", MANAGER, STAFF),
            def("NOTIFICATION_MANAGE", "NOTIFICATION", "Soạn, gửi, sửa, xóa thông báo", MANAGER),
            def("FEEDBACK_CREATE", "FEEDBACK", "Gửi phản ánh", MANAGER, STUDENT),
            def("FEEDBACK_VIEW", "FEEDBACK", "Xem toàn bộ phản ánh", MANAGER, STAFF),
            def("FEEDBACK_MANAGE", "FEEDBACK", "Phản hồi phản ánh", MANAGER, STAFF),
            // 9. An ninh và khách ra vào
            def("VISITOR_VIEW", "SECURITY", "Xem lịch sử khách", MANAGER, STAFF),
            def("VISITOR_MANAGE", "SECURITY", "Check-in/check-out khách, ghi nhận sự kiện an ninh", MANAGER, STAFF));

    /**
     * @param rolesByCode map roleCode -> Role đã tồn tại trong DB (ADMIN, MANAGER,
     *                    STAFF, STUDENT)
     */
    public void seed(Map<String, Role> rolesByCode) {
        int createdPermissions = 0;
        int createdLinks = 0;

        for (Def def : DEFINITIONS) {
            Permission permission = permissionRepository.findByPermissionCode(def.code()).orElse(null);
            if (permission == null) {
                permission = permissionRepository.save(Permission.builder()
                        .permissionCode(def.code())
                        .module(def.module())
                        .description(def.description())
                        .build());
                createdPermissions++;
            }

            List<String> grantedRoles = new ArrayList<>();
            grantedRoles.add("ADMIN");
            grantedRoles.addAll(def.roles());

            for (String roleCode : grantedRoles) {
                Role role = rolesByCode.get(roleCode);
                if (role == null) {
                    continue;
                }
                RolePermissionId id = new RolePermissionId(role.getRoleId(), permission.getPermissionId());
                if (!rolePermissionRepository.existsById(id)) {
                    rolePermissionRepository.save(RolePermission.builder()
                            .roleId(role.getRoleId())
                            .permissionId(permission.getPermissionId())
                            .build());
                    createdLinks++;
                }
            }
        }

        log.info("RBAC seed: thêm {} quyền mới, {} liên kết vai trò-quyền mới", createdPermissions, createdLinks);
    }
}
