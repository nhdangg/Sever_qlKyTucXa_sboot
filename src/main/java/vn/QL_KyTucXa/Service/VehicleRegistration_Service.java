package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.VehicleRegistration_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.VehicleRegistration_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.VehicleRegistration;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.VehicleRegistration_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.VehicleRegistration_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VehicleRegistration_Service {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "ACTIVE", "PENDING", "REJECTED", "EXPIRED", "CANCELLED");

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "vehicleId", "licensePlate", "vehicleType", "status", "registrationDate", "expiryDate", "monthlyFee", "createdAt");

    VehicleRegistration_Repository vehicleRepository;
    Student_Repository studentRepository;
    Contract_Repository contractRepository;
    CurrentUser_Service currentUserService;
    SystemSetting_Service systemSettingService;
    VehicleRegistration_Mapper vehicleMapper;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    // Chuẩn hóa biển số xe: loại bỏ dấu chấm, gạch ngang, khoảng trắng và chuyển
    // sang chữ hoa
    private String normalizeLicensePlate(String plate) {
        if (plate == null)
            return null;
        return plate.replaceAll("[.\\-\\s]", "").toUpperCase();
    }

    // Chuẩn hóa và kiểm tra loại xe hợp lệ
    private String normalizeVehicleType(String type) {
        if (type == null || type.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Loại xe không được để trống");
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "MOTORBIKE", "XE_MAY" -> "MOTORBIKE";
            case "ELECTRIC_BICYCLE", "XE_DAP_DIEN" -> "ELECTRIC_BICYCLE";
            case "BICYCLE", "XE_DAP" -> "BICYCLE";
            default -> throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Loại xe không hợp lệ. Hệ thống chỉ hỗ trợ: MOTORBIKE (Xe máy), ELECTRIC_BICYCLE (Xe đạp điện), BICYCLE (Xe đạp)");
        };
    }

    // Xác định mức phí gửi xe hàng tháng theo cấu hình hệ thống
    private BigDecimal resolveMonthlyFee(String normalizedType) {
        return switch (normalizedType) {
            case "MOTORBIKE" -> systemSettingService != null
                    ? systemSettingService.getDecimalSetting("PARKING_FEE_MOTORBIKE", new BigDecimal("100000"))
                    : new BigDecimal("100000");
            case "ELECTRIC_BICYCLE" -> systemSettingService != null
                    ? systemSettingService.getDecimalSetting("PARKING_FEE_ELECTRIC_BICYCLE", new BigDecimal("70000"))
                    : new BigDecimal("70000");
            case "BICYCLE" -> systemSettingService != null
                    ? systemSettingService.getDecimalSetting("PARKING_FEE_BICYCLE", new BigDecimal("30000"))
                    : new BigDecimal("30000");
            default -> new BigDecimal("50000");
        };
    }

    // Xác định hồ sơ sinh viên: lấy từ JWT nếu là sinh viên hoặc không truyền
    // studentId
    private Student resolveStudent(String studentId) {
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (isStaffOrAdmin && studentId != null && !studentId.isBlank()) {
            return studentRepository.findById(studentId)
                    .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        }

        return currentUserService.findStudentId()
                .flatMap(studentRepository::findById)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Đăng ký gửi phương tiện mới
    @Transactional
    public VehicleRegistration_Response create(VehicleRegistration_Request request) {
        Student student = resolveStudent(request.getStudentId());
        log.info("Đăng ký gửi xe cho sinh viên: {}, Mã SV: {}", student.getFullName(), student.getStudentCode());

        // Kiểm tra sinh viên có hợp đồng nội trú đang hiệu lực hay không
        boolean hasActiveContract = contractRepository.existsByStudent_StudentIdAndStatus(student.getStudentId(),
                "ACTIVE");
        if (!hasActiveContract) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên phải đang có hợp đồng nội trú hiệu lực (ACTIVE) tại KTX để đăng ký gửi xe");
        }

        // Giới hạn số lượng xe tối đa mỗi sinh viên được phép đăng ký
        int maxVehicles = systemSettingService != null
                ? systemSettingService.getIntSetting("MAX_VEHICLES_PER_STUDENT", 1)
                : 1;
        long currentActiveCount = vehicleRepository.countByStudent_StudentIdAndStatusIn(
                student.getStudentId(), List.of("ACTIVE", "PENDING"));
        if (currentActiveCount >= maxVehicles) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mỗi sinh viên chỉ được đăng ký tối đa " + maxVehicles
                    + " phương tiện đang hoạt động hoặc chờ duyệt");
        }

        String normalizedType = normalizeVehicleType(request.getVehicleType());
        String cleanPlate = normalizeLicensePlate(request.getLicensePlate());

        // Xe máy và xe đạp điện bắt buộc phải có biển số
        if (("MOTORBIKE".equals(normalizedType) || "ELECTRIC_BICYCLE".equals(normalizedType))
                && (cleanPlate == null || cleanPlate.isBlank())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phương tiện loại xe máy/xe đạp điện bắt buộc phải có biển số xe");
        }

        // Kiểm tra trùng lặp biển số đang ACTIVE hoặc PENDING
        if (cleanPlate != null && !cleanPlate.isBlank()) {
            boolean duplicate = vehicleRepository.findByLicensePlate(cleanPlate).stream()
                    .anyMatch(v -> List.of("ACTIVE", "PENDING").contains(v.getStatus()));
            if (duplicate) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Biển số xe '" + cleanPlate + "' đã được đăng ký và đang hoạt động hoặc chờ duyệt");
            }
        }

        // Phí gửi xe do hệ thống quyết định theo cấu hình bảng SystemSetting, không phụ
        // thuộc vào client
        BigDecimal monthlyFee = resolveMonthlyFee(normalizedType);

        // Hạn đăng ký: lấy theo ngày kết thúc hợp đồng nội trú ACTIVE gần nhất của sinh
        // viên
        LocalDate expiryDate = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(), "ACTIVE")
                .stream()
                .map(Contract::getEndDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now().plusMonths(6));

        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        String initialStatus = isStaffOrAdmin && request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus().trim().toUpperCase()
                : "PENDING";

        LocalDate regDate = request.getRegistrationDate() != null && isStaffOrAdmin
                ? request.getRegistrationDate()
                : LocalDate.now();

        VehicleRegistration vehicle = VehicleRegistration.builder()
                .student(student)
                .vehicleType(normalizedType)
                .licensePlate(cleanPlate)
                .brandModel(request.getBrandModel())
                .monthlyFee(monthlyFee)
                .registrationDate(regDate)
                .expiryDate(expiryDate)
                .status(initialStatus)
                .build();

        vehicle = vehicleRepository.save(vehicle);

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX nếu sinh viên đăng ký
        if (notificationService != null && student != null) {
            String title = "Đăng ký gửi xe mới từ SV " + student.getFullName();
            String content = String.format(
                    "Sinh viên %s (MSSV: %s) vừa đăng ký gửi phương tiện %s - Biển số: %s. Trạng thái: %s.",
                    student.getFullName(),
                    student.getStudentCode(),
                    normalizedType,
                    cleanPlate != null ? cleanPlate : "Không có",
                    initialStatus);
            notificationService.notifyStaffAndAdmins(title, content, student.getUser(), cleanPlate);
        }

        return vehicleMapper.toResponse(vehicle);
    }

    // Duyệt hoặc từ chối đơn đăng ký gửi xe
    @Transactional
    public VehicleRegistration_Response approve(String vehicleId, boolean approved) {
        VehicleRegistration vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gửi xe"));

        if (!"PENDING".equalsIgnoreCase(vehicle.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể duyệt bản đăng ký ở trạng thái PENDING (Hiện tại: " + vehicle.getStatus() + ")");
        }

        if (approved) {
            vehicle.setStatus("ACTIVE");
            vehicle.setRegistrationDate(LocalDate.now());
        } else {
            vehicle.setStatus("REJECTED");
        }

        vehicle = vehicleRepository.save(vehicle);

        // Bắn thông báo cho sinh viên khi được duyệt hoặc từ chối gửi xe
        if (notificationService != null && vehicle.getStudent() != null) {
            String title = approved ? "Đăng ký xe của bạn đã được duyệt!" : "Đăng ký xe không được duyệt";
            String content = String.format("Phương tiện biển số %s (%s) của bạn đã %s.",
                    vehicle.getLicensePlate() != null ? vehicle.getLicensePlate() : "",
                    vehicle.getVehicleType(),
                    approved ? "được kích hoạt thành công" : "bị từ chối duyệt");
            notificationService.notifyStudent(vehicle.getStudent(), title, content, null);
        }

        return vehicleMapper.toResponse(vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleRegistration_Response getById(String vehicleId) {
        VehicleRegistration vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gửi xe"));

        checkAccessOwnership(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleRegistration_Response> getByStudentId(String studentId) {
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (!currentStudentId.equals(studentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }

        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<VehicleRegistration> list = vehicleRepository.findByStudent_StudentId(studentId);
        return vehicleMapper.toResponseList(list);
    }

    // Kiểm tra quyền truy cập: sinh viên chỉ được xem/sửa phương tiện của chính
    // mình
    private void checkAccessOwnership(VehicleRegistration vehicle) {
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (vehicle.getStudent() == null || !vehicle.getStudent().getStudentId().equals(currentStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }
    }

    // Tìm kiếm phương tiện gửi có phân trang với nhiều bộ lọc
    @Transactional(readOnly = true)
    public Page_Response<VehicleRegistration_Response> search(
            String licensePlate,
            String keyword,
            String vehicleType,
            String status,
            Boolean expiringSoon,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền tra cứu toàn bộ phương tiện");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "registrationDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String cleanPlate = licensePlate != null && !licensePlate.isBlank() ? normalizeLicensePlate(licensePlate)
                : null;
        LocalDate expiringBefore = Boolean.TRUE.equals(expiringSoon) ? LocalDate.now().plusDays(30) : null;

        Page<VehicleRegistration> pageResult = vehicleRepository.searchVehicles(
                cleanPlate,
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (vehicleType != null && !vehicleType.isBlank()) ? vehicleType.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                expiringBefore,
                pageable);

        List<VehicleRegistration_Response> data = vehicleMapper.toResponseList(pageResult.getContent());

        return Page_Response.<VehicleRegistration_Response>builder()
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
                .data(data)
                .build();
    }

    @Transactional(readOnly = true)
    public Page_Response<VehicleRegistration_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return search(null, null, null, null, null, page, size, sortBy, sortDir);
    }

    // Cập nhật thông tin gửi xe
    @Transactional
    public VehicleRegistration_Response update(String vehicleId, VehicleRegistration_Request request) {
        VehicleRegistration vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gửi xe"));

        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (vehicle.getStudent() == null || !vehicle.getStudent().getStudentId().equals(currentStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
            // Sinh viên chỉ được sửa dòng xe hoặc biển số khi còn đang chờ duyệt PENDING
            if (!"PENDING".equalsIgnoreCase(vehicle.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể chỉnh sửa thông tin xe đã được duyệt hoạt động");
            }
        }

        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            String cleanPlate = normalizeLicensePlate(request.getLicensePlate());
            if (!cleanPlate.equals(vehicle.getLicensePlate())) {
                boolean duplicate = vehicleRepository.findByLicensePlate(cleanPlate).stream()
                        .anyMatch(v -> !v.getVehicleId().equals(vehicleId)
                                && List.of("ACTIVE", "PENDING").contains(v.getStatus()));
                if (duplicate) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Biển số xe '" + cleanPlate + "' đã được đăng ký cho phương tiện khác");
                }
                vehicle.setLicensePlate(cleanPlate);
            }
        }

        if (request.getBrandModel() != null) {
            vehicle.setBrandModel(request.getBrandModel());
        }

        // Chỉ cán bộ quản lý mới được thay đổi trạng thái, loại xe, đơn giá và ngày hết
        // hạn
        if (isStaffOrAdmin) {
            if (request.getVehicleType() != null && !request.getVehicleType().isBlank()) {
                String normalizedType = normalizeVehicleType(request.getVehicleType());
                vehicle.setVehicleType(normalizedType);
                vehicle.setMonthlyFee(resolveMonthlyFee(normalizedType));
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                String normalizedStatus = request.getStatus().trim().toUpperCase();
                if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Trạng thái đăng ký xe '" + request.getStatus() + "' không hợp lệ. Cho phép: "
                                    + ALLOWED_STATUSES);
                }
                vehicle.setStatus(normalizedStatus);
            }
            if (request.getExpiryDate() != null) {
                vehicle.setExpiryDate(request.getExpiryDate());
            }
            if (request.getMonthlyFee() != null) {
                vehicle.setMonthlyFee(request.getMonthlyFee());
            }
        }

        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    // Xóa mềm phương tiện: chuyển trạng thái thành CANCELLED để lưu vết tính phí
    @Transactional
    public void delete(String vehicleId) {
        VehicleRegistration vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gửi xe"));

        checkAccessOwnership(vehicle);

        vehicle.setStatus("CANCELLED");
        vehicleRepository.save(vehicle);
    }

    // Tác vụ định kỳ quét và cập nhật trạng thái EXPIRED cho các phương tiện quá
    // hạn đăng ký
    @Scheduled(cron = "0 10 0 * * ?")
    @Transactional
    public int autoExpireVehicles() {
        LocalDate today = LocalDate.now();
        List<VehicleRegistration> expiredVehicles = vehicleRepository.findByStatusAndExpiryDateBefore("ACTIVE", today);
        if (expiredVehicles.isEmpty()) {
            return 0;
        }
        for (VehicleRegistration v : expiredVehicles) {
            v.setStatus("EXPIRED");
        }
        vehicleRepository.saveAll(expiredVehicles);
        log.info("Đã tự động chuyển trạng thái EXPIRED cho {} phương tiện hết hạn", expiredVehicles.size());
        return expiredVehicles.size();
    }
}
