package vn.QL_KyTucXa.Config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Repository.*;

import java.math.BigDecimal;
// import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InitAdminAccountConfig implements ApplicationRunner {

        User_Repository userRepository;
        Role_Repository roleRepository;
        ServiceType_Repository serviceTypeRepository;
        // Staff_Repository staffRepository;
        // Student_Repository studentRepository;
        // ViolationType_Repository violationTypeRepository;
        PasswordEncoder passwordEncoder;
        RbacSeeder rbacSeeder;

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                log.info("========== BẮT ĐẦU KHỞI TẠO TÀI KHOẢN VÀ DỮ LIỆU MẪU ĐỂ TEST ==========");

                // 1. Khởi tạo các Vai trò (Roles): ADMIN, MANAGER, STAFF, STUDENT
                Role adminRole = getOrCreateRole("ADMIN", "Quản trị viên",
                                "Vai trò quản trị cao nhất hệ thống ký túc xá");
                Role managerRole = getOrCreateRole("MANAGER", "Quản lý KTX",
                                "Trưởng ban quản lý: duyệt hợp đồng, quản lý cơ sở vật chất và chính sách");
                Role staffRole = getOrCreateRole("STAFF", "Nhân viên", "Nhân viên quản lý ký túc xá và cơ sở vật chất");
                Role studentRole = getOrCreateRole("STUDENT", "Sinh viên", "Sinh viên lưu trú tại ký túc xá");

                // 1b. Danh mục quyền và gán quyền cho từng vai trò (ma trận RBAC)
                rbacSeeder.seed(Map.of(
                                "ADMIN", adminRole,
                                "MANAGER", managerRole,
                                "STAFF", staffRole,
                                "STUDENT", studentRole));

                // 2. Khởi tạo tài khoản Quản trị viên: admin / admin (Có xác thực 2FA Telegram)
                initAdminAccount(adminRole);

                // 2b. Đảm bảo danh mục dịch vụ Điện & Nước mặc định luôn tồn tại để ghi chỉ số
                // công tơ
                initDefaultServiceTypes();

                // // 3. Khởi tạo tài khoản Quản lý để test: manager / manager
                // initStaffAccount("manager", "manager", "QL001", "Phạm Quốc Quản Lý",
                // "Trưởng ban quản lý KTX", "Ban Quản Lý KTX", "0977665544",
                // "manager@kytucxa.edu.vn",
                // managerRole);

                // // 3b. Khởi tạo tài khoản Nhân viên để test: nhanvien / nhanvien
                // initStaffAccount("nhanvien", "nhanvien", "NV001", "Nguyễn Văn Quản Lý",
                // "Quản lý tòa nhà", "Ban Quản Lý KTX", "0988776655",
                // "nhanvien@kytucxa.edu.vn",
                // staffRole);

                // // 4. Khởi tạo tài khoản Sinh viên 1 để test: sinhvien / sinhvien
                // initStudentAccount("sinhvien", "sinhvien", "SV001", "Trần Thị Sinh Viên",
                // "FEMALE", LocalDate.of(2004, 10, 20), "001204000123", "Công nghệ thông tin",
                // "K68-CNTT", "Hà Nội", "0911223344", "sinhvien@kytucxa.edu.vn", studentRole);

                // // 5. Khởi tạo thêm tài khoản Sinh viên 2 (Nam): student / student
                // initStudentAccount("student", "student", "SV002", "Lê Hoàng Nam",
                // "MALE", LocalDate.of(2004, 5, 15), "001204000456", "Điện tử viễn thông",
                // "K68-DTVT", "Đà Nẵng", "0933445566", "student@kytucxa.edu.vn", studentRole);

                // // 6. Khởi tạo dữ liệu hạ tầng KTX mẫu (Tòa nhà, Tầng, Loại phòng, Phòng,
                // // Giường, Hợp đồng, Hóa đơn, Loại vi phạm)
                // seedSampleInfrastructure();

                log.info("========== HOÀN TẤT KHỞI TẠO TÀI KHOẢN TEST THÀNH CÔNG ==========");
        }

        // private void seedSampleInfrastructure() {
        // try {

        // // 6.8. Loại vi phạm mẫu
        // if (violationTypeRepository.count() == 0) {
        // violationTypeRepository.save(ViolationType.builder()
        // .violationName("Về muộn sau giờ đóng cổng (23h00)")
        // .severityLevel("MINOR")
        // .penaltyPoints(5)
        // .fineAmount(BigDecimal.ZERO)
        // .description("Sinh viên về KTX sau 23h00 không có lý do chính đáng")
        // .build());
        // violationTypeRepository.save(ViolationType.builder()
        // .violationName("Sử dụng thiết bị nấu nướng công suất lớn trong phòng")
        // .severityLevel("MAJOR")
        // .penaltyPoints(15)
        // .fineAmount(new BigDecimal("200000.00"))
        // .description("Vi phạm quy định an toàn phòng cháy chữa cháy KTX")
        // .build());
        // }
        // } catch (Exception e) {
        // log.warn("Khởi tạo dữ liệu hạ tầng mẫu bỏ qua lỗi nhỏ: {}", e.getMessage());
        // }
        // }

        private Role getOrCreateRole(String roleCode, String roleName, String description) {
                return roleRepository.findByRoleCode(roleCode)
                                .orElseGet(() -> {
                                        log.info("Tạo mới vai trò: {}", roleCode);
                                        Role role = Role.builder()
                                                        .roleCode(roleCode)
                                                        .roleName(roleName)
                                                        .description(description)
                                                        .build();
                                        return roleRepository.save(role);
                                });
        }

        private void initAdminAccount(Role adminRole) {
                Optional<User> adminOpt = userRepository.findByUsername("admin");

                if (adminOpt.isEmpty()) {
                        User adminUser = User.builder()
                                        .username("admin")
                                        .passwordHash(passwordEncoder.encode("admin"))
                                        .email("admin@kytucxa.edu.vn")
                                        .phone("0999999999")
                                        .telegramChatId(null)
                                        .role(adminRole)
                                        .status("ACTIVE")
                                        .isEmailVerified(true)
                                        .failedLoginAttempts(0)
                                        .build();

                        userRepository.save(adminUser);
                        log.info("=> ĐÃ TẠO TÀI KHOẢN ADMIN: Username = 'admin', Password = 'admin' (Role: ADMIN)");
                } else {
                        User existingAdmin = adminOpt.get();
                        boolean updated = false;

                        if (existingAdmin.getRole() == null
                                        || !"ADMIN".equalsIgnoreCase(existingAdmin.getRole().getRoleCode())) {
                                existingAdmin.setRole(adminRole);
                                updated = true;
                        }

                        if (updated) {
                                userRepository.save(existingAdmin);
                                log.info("=> Đã cập nhật quyền ADMIN cho tài khoản 'admin'");
                        }
                }
        }

        private void initDefaultServiceTypes() {
                try {
                        List<ServiceType> allServices = serviceTypeRepository.findAll();
                        boolean hasElectricity = allServices.stream().anyMatch(s -> "METERED"
                                        .equalsIgnoreCase(s.getBillingType()) &&
                                        ((s.getServiceCode() != null
                                                        && s.getServiceCode().toUpperCase().contains("DIEN")) ||
                                                        (s.getServiceName() != null && s.getServiceName().toLowerCase()
                                                                        .contains("điện"))));
                        if (!hasElectricity && !serviceTypeRepository.existsByServiceCode("DIEN_SH")) {
                                serviceTypeRepository.save(ServiceType.builder()
                                                .serviceCode("DIEN_SH")
                                                .serviceName("Điện sinh hoạt")
                                                .unit("kWh")
                                                .unitPrice(new BigDecimal("3500.00"))
                                                .billingType("METERED")
                                                .description("Điện sinh hoạt tính theo chỉ số công tơ phòng")
                                                .build());
                                log.info("=> Đã khởi tạo dịch vụ mặc định: DIEN_SH (3.500 đ/kWh)");
                        }

                        boolean hasWater = allServices.stream().anyMatch(s -> "METERED"
                                        .equalsIgnoreCase(s.getBillingType()) &&
                                        ((s.getServiceCode() != null
                                                        && s.getServiceCode().toUpperCase().contains("NUOC")) ||
                                                        (s.getServiceName() != null && s.getServiceName().toLowerCase()
                                                                        .contains("nước"))));
                        if (!hasWater && !serviceTypeRepository.existsByServiceCode("NUOC_SH")) {
                                serviceTypeRepository.save(ServiceType.builder()
                                                .serviceCode("NUOC_SH")
                                                .serviceName("Nước sạch sinh hoạt")
                                                .unit("m³")
                                                .unitPrice(new BigDecimal("12000.00"))
                                                .billingType("METERED")
                                                .description("Nước sạch sinh hoạt tính theo đồng hồ đo của phòng")
                                                .build());
                                log.info("=> Đã khởi tạo dịch vụ mặc định: NUOC_SH (12.000 đ/m³)");
                        }
                } catch (Exception e) {
                        log.warn("Không thể khởi tạo danh mục dịch vụ Điện/Nước mặc định: {}", e.getMessage());
                }
        }
}
