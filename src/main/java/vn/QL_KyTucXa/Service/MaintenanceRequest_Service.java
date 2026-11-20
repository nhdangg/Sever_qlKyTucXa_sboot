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
import vn.QL_KyTucXa.DTO.Request.MaintenanceRequest_Request;
import vn.QL_KyTucXa.DTO.Response.MaintenanceRequest_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.MaintenanceRequest;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.MaintenanceRequest_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.MaintenanceRequest_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceRequest_Service {

    private static final Set<String> ALLOWED_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "requestId", "requestCode", "category", "priority", "status", "requestedDate", "completedDate"
    );

    MaintenanceRequest_Repository maintenanceRepository;
    Room_Repository roomRepository;
    User_Repository userRepository;
    Staff_Repository staffRepository;
    Student_Repository studentRepository;
    Contract_Repository contractRepository;
    CurrentUser_Service currentUserService;
    MaintenanceRequest_Mapper maintenanceMapper;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    // Tạo yêu cầu bảo trì sửa chữa mới
    @Transactional
    public MaintenanceRequest_Response create(MaintenanceRequest_Request request) {
        log.info("Tạo yêu cầu bảo trì: {}", request.getRequestCode());

        User nguoi_bao_cao = resolveReporter(request.getReportedBy());
        Room phong = resolveRoomForMaintenance(request.getRoomId(), nguoi_bao_cao);

        String requestCode = request.getRequestCode();
        if (requestCode == null || requestCode.isBlank()) {
            requestCode = generateRequestCode();
        } else {
            requestCode = requestCode.trim();
            if (maintenanceRepository.existsByRequestCode(requestCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Mã yêu cầu '" + requestCode + "' đã tồn tại");
            }
        }

        MaintenanceRequest yeu_cau_sua_chua = maintenanceMapper.toEntity(request);
        yeu_cau_sua_chua.setRequestCode(requestCode);
        yeu_cau_sua_chua.setRoom(phong);
        yeu_cau_sua_chua.setReportedBy(nguoi_bao_cao);

        String priority = request.getPriority();
        if (priority == null || priority.isBlank() || "NORMAL".equalsIgnoreCase(priority)) {
            priority = "MEDIUM";
        } else {
            priority = priority.trim().toUpperCase();
            if (!ALLOWED_PRIORITIES.contains(priority)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mức độ ưu tiên '" + priority + "' không hợp lệ. Các giá trị cho phép: " + ALLOWED_PRIORITIES);
            }
        }
        yeu_cau_sua_chua.setPriority(priority);

        // Khởi tạo yêu cầu ở trạng thái PENDING
        yeu_cau_sua_chua.setStatus("PENDING");
        yeu_cau_sua_chua.setCompletedDate(null);
        yeu_cau_sua_chua.setResolutionNote(null);

        // Phân công nhân viên phụ trách nếu có và không phải sinh viên
        boolean isStudent = currentUserService != null && currentUserService.hasRole("STUDENT");
        if (!isStudent && request.getAssignedTo() != null && !request.getAssignedTo().isBlank()) {
            Staff nhan_vien = staffRepository.findById(request.getAssignedTo().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
            yeu_cau_sua_chua.setAssignedTo(nhan_vien);
            yeu_cau_sua_chua.setStatus("IN_PROGRESS");
        } else {
            yeu_cau_sua_chua.setAssignedTo(null);
        }

        yeu_cau_sua_chua = maintenanceRepository.save(yeu_cau_sua_chua);

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null) {
            String roomNum = (phong != null) ? phong.getRoomNumber() : "";
            String reporterName = nguoi_bao_cao.getUsername();
            String title = "Yêu cầu sửa chữa mới: Phòng " + roomNum;
            String content = String.format("Sinh viên %s vừa gửi yêu cầu sửa chữa tại phòng %s. Mã yêu cầu: %s. Hạng mục: %s. Mức độ: %s. Mô tả: %s",
                    reporterName,
                    roomNum,
                    yeu_cau_sua_chua.getRequestCode(),
                    request.getCategory() != null ? request.getCategory() : "Cơ sở vật chất",
                    priority,
                    request.getDescription());
            notificationService.notifyStaffAndAdmins(title, content, nguoi_bao_cao, yeu_cau_sua_chua.getRequestCode());
        }

        return maintenanceMapper.toResponse(yeu_cau_sua_chua);
    }

    // Xác định người dùng báo cáo yêu cầu bảo trì ưu tiên lấy từ JWT
    private User resolveReporter(String requestReportedBy) {
        if (currentUserService == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String userId;
        try {
            if (currentUserService.hasPermission("MAINTENANCE_MANAGE")
                    && requestReportedBy != null
                    && !requestReportedBy.isBlank()
                    && !"self".equalsIgnoreCase(requestReportedBy)) {
                userId = requestReportedBy.trim();
            } else {
                userId = currentUserService.userId();
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    // Xác định phòng cần sửa chữa ưu tiên phòng theo yêu cầu hoặc phòng trong hợp đồng ACTIVE của sinh viên
    private Room resolveRoomForMaintenance(String roomId, User reporter) {
        Room phong_sinh_vien_dang_o = null;
        if (studentRepository != null && contractRepository != null) {
            Optional<Student> sinh_vien_tuy_chon = studentRepository.findByUser_UserId(reporter.getUserId());
            if (sinh_vien_tuy_chon.isPresent()) {
                List<Contract> danh_sach_hop_dong = contractRepository.findByStudent_StudentId(sinh_vien_tuy_chon.get().getStudentId());
                for (Contract hop_dong : danh_sach_hop_dong) {
                    if ("ACTIVE".equalsIgnoreCase(hop_dong.getStatus()) && hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                        phong_sinh_vien_dang_o = hop_dong.getBed().getRoom();
                        break;
                    }
                }
            }
        }

        boolean isStudent = currentUserService != null && currentUserService.hasRole("STUDENT");
        if (isStudent) {
            if (phong_sinh_vien_dang_o == null) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên phải có hợp đồng nội trú ACTIVE để yêu cầu sửa chữa");
            }
            if (roomId != null && !roomId.isBlank() && !roomId.equals(phong_sinh_vien_dang_o.getRoomId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên chỉ được yêu cầu sửa chữa cho đúng phòng mình đang ở");
            }
            return phong_sinh_vien_dang_o;
        }

        if (roomId != null && !roomId.isBlank()) {
            return roomRepository.findById(roomId.trim())
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        }

        if (phong_sinh_vien_dang_o != null) {
            return phong_sinh_vien_dang_o;
        }

        throw new AppException(ErrorCode.ROOM_NOT_FOUND, "Không tìm thấy phòng hợp lệ từ hợp đồng đang hoạt động của sinh viên");
    }

    // Tự sinh mã yêu cầu bảo trì theo định dạng MR-yyyyMMdd-XXX
    private String generateRequestCode() {
        String prefix = "MR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        int counter = 1;
        String candidate;
        do {
            candidate = String.format("%s%03d", prefix, counter++);
        } while (maintenanceRepository.existsByRequestCode(candidate));
        return candidate;
    }

    // Lấy thông tin yêu cầu bảo trì theo ID có kiểm tra quyền sở hữu đối với sinh viên
    @Transactional(readOnly = true)
    public MaintenanceRequest_Response getById(String requestId) {
        MaintenanceRequest yeu_cau_sua_chua = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentUserId = currentUserService.userId();
            if (yeu_cau_sua_chua.getReportedBy() == null || !yeu_cau_sua_chua.getReportedBy().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền truy cập yêu cầu bảo trì của người khác");
            }
        }

        return maintenanceMapper.toResponse(yeu_cau_sua_chua);
    }

    // Lấy danh sách yêu cầu bảo trì theo phòng
    @Transactional(readOnly = true)
    public List<MaintenanceRequest_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String studentId = currentUserService.studentId();
            List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStudent_StudentIdAndStatus(studentId, "ACTIVE");
            boolean livesInRoom = danh_sach_hop_dong_hieu_luc.stream()
                    .anyMatch(c -> c.getBed() != null && c.getBed().getRoom() != null && roomId.equals(c.getBed().getRoom().getRoomId()));
            if (!livesInRoom) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn chỉ có thể xem yêu cầu bảo trì của phòng bạn đang ở");
            }
        }

        List<MaintenanceRequest> danh_sach_yeu_cau = maintenanceRepository.findByRoom_RoomId(roomId);
        return maintenanceMapper.toResponseList(danh_sach_yeu_cau);
    }

    // Lấy danh sách yêu cầu bảo trì phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<MaintenanceRequest_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và phân trang danh sách yêu cầu bảo trì theo từ khóa, trạng thái, mức ưu tiên, số phòng, danh mục
    @Transactional(readOnly = true)
    public Page_Response<MaintenanceRequest_Response> getAll(
            int page,
            int size,
            String keyword,
            String status,
            String priority,
            String roomNumber,
            String category,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xem toàn bộ yêu cầu bảo trì");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "requestedDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String st = (status != null && !status.isBlank()) ? status.trim() : null;
        String pr = (priority != null && !priority.isBlank()) ? priority.trim().toUpperCase() : null;
        String rn = (roomNumber != null && !roomNumber.isBlank()) ? roomNumber.trim() : null;
        String cat = (category != null && !category.isBlank()) ? category.trim() : null;

        Page<MaintenanceRequest> trang_yeu_cau = maintenanceRepository.searchMaintenanceRequests(kw, st, pr, rn, cat, pageable);
        List<MaintenanceRequest_Response> danh_sach_ket_qua = maintenanceMapper.toResponseList(trang_yeu_cau.getContent());

        return Page_Response.<MaintenanceRequest_Response>builder()
                .currentPage(trang_yeu_cau.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_yeu_cau.getTotalPages())
                .totalElements(trang_yeu_cau.getTotalElements())
                .hasPrevious(trang_yeu_cau.hasPrevious())
                .hasNext(trang_yeu_cau.hasNext())
                .isFirst(trang_yeu_cau.isFirst())
                .isLast(trang_yeu_cau.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Phân công nhân viên phụ trách xử lý yêu cầu bảo trì
    @Transactional
    public MaintenanceRequest_Response assignStaff(String requestId, String staffId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền phân công nhân viên sửa chữa");
        }
        MaintenanceRequest yeu_cau_sua_chua = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if ("COMPLETED".equalsIgnoreCase(yeu_cau_sua_chua.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân công cho yêu cầu bảo trì đã hoàn thành");
        }
        if ("CANCELLED".equalsIgnoreCase(yeu_cau_sua_chua.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân công cho yêu cầu bảo trì đã bị hủy");
        }

        Staff nhan_vien = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        yeu_cau_sua_chua.setAssignedTo(nhan_vien);
        yeu_cau_sua_chua.setStatus("IN_PROGRESS");
        yeu_cau_sua_chua = maintenanceRepository.save(yeu_cau_sua_chua);

        return maintenanceMapper.toResponse(yeu_cau_sua_chua);
    }

    // Hoàn thành xử lý yêu cầu bảo trì
    @Transactional
    public MaintenanceRequest_Response complete(String requestId, String resolutionNote) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xác nhận hoàn thành sửa chữa");
        }
        MaintenanceRequest yeu_cau_sua_chua = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (yeu_cau_sua_chua.getAssignedTo() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Yêu cầu bảo trì phải được phân công nhân sự trước khi hoàn thành");
        }
        if ("COMPLETED".equalsIgnoreCase(yeu_cau_sua_chua.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Yêu cầu bảo trì đã được hoàn thành trước đó");
        }
        if ("CANCELLED".equalsIgnoreCase(yeu_cau_sua_chua.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể hoàn thành yêu cầu bảo trì đã bị hủy");
        }

        yeu_cau_sua_chua.setStatus("COMPLETED");
        yeu_cau_sua_chua.setCompletedDate(LocalDateTime.now());
        yeu_cau_sua_chua.setResolutionNote(resolutionNote);
        yeu_cau_sua_chua = maintenanceRepository.save(yeu_cau_sua_chua);

        return maintenanceMapper.toResponse(yeu_cau_sua_chua);
    }

    // Lấy danh sách yêu cầu bảo trì theo ID người báo cáo có kiểm tra quyền sở hữu đối với sinh viên
    @Transactional(readOnly = true)
    public List<MaintenanceRequest_Response> getByReporterUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentUserId = currentUserService.userId();
            if (!userId.equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem yêu cầu bảo trì của người khác");
            }
        }

        return maintenanceMapper.toResponseList(maintenanceRepository.findByReportedBy_UserId(userId));
    }

    // Hủy yêu cầu bảo trì (chuyển trạng thái sang CANCELLED khi đang PENDING)
    @Transactional
    public MaintenanceRequest_Response cancel(String requestId, String reason) {
        MaintenanceRequest yeu_cau_sua_chua = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (!"PENDING".equalsIgnoreCase(yeu_cau_sua_chua.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể hủy yêu cầu bảo trì khi đang ở trạng thái PENDING");
        }

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentUserId = currentUserService.userId();
            if (yeu_cau_sua_chua.getReportedBy() == null || !yeu_cau_sua_chua.getReportedBy().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy yêu cầu bảo trì của người khác");
            }
        }

        yeu_cau_sua_chua.setStatus("CANCELLED");
        yeu_cau_sua_chua.setResolutionNote((reason != null && !reason.isBlank()) ? reason.trim() : "Người dùng đã hủy yêu cầu");
        yeu_cau_sua_chua = maintenanceRepository.save(yeu_cau_sua_chua);

        return maintenanceMapper.toResponse(yeu_cau_sua_chua);
    }

    // Xóa yêu cầu bảo trì
    @Transactional
    public void delete(String requestId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xóa yêu cầu sửa chữa");
        }
        MaintenanceRequest yeu_cau_sua_chua = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        maintenanceRepository.delete(yeu_cau_sua_chua);
    }
}
