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

        User reporter = resolveReporter(request.getReportedBy());
        Room room = resolveRoomForMaintenance(request.getRoomId(), reporter);

        String requestCode = request.getRequestCode();
        if (requestCode == null || requestCode.isBlank()) {
            requestCode = generateRequestCode();
        } else {
            requestCode = requestCode.trim();
            if (maintenanceRepository.existsByRequestCode(requestCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Mã yêu cầu '" + requestCode + "' đã tồn tại");
            }
        }

        MaintenanceRequest maintenance = maintenanceMapper.toEntity(request);
        maintenance.setRequestCode(requestCode);
        maintenance.setRoom(room);
        maintenance.setReportedBy(reporter);

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
        maintenance.setPriority(priority);

        // Khởi tạo yêu cầu ở trạng thái PENDING
        maintenance.setStatus("PENDING");
        maintenance.setCompletedDate(null);
        maintenance.setResolutionNote(null);

        // Phân công nhân viên phụ trách nếu có và không phải sinh viên
        boolean isStudent = currentUserService != null && currentUserService.hasRole("STUDENT");
        if (!isStudent && request.getAssignedTo() != null && !request.getAssignedTo().isBlank()) {
            Staff staff = staffRepository.findById(request.getAssignedTo().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
            maintenance.setAssignedTo(staff);
            maintenance.setStatus("IN_PROGRESS");
        } else {
            maintenance.setAssignedTo(null);
        }

        maintenance = maintenanceRepository.save(maintenance);

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null) {
            String roomNum = (room != null) ? room.getRoomNumber() : "";
            String reporterName = reporter.getUsername();
            String title = "Yêu cầu sửa chữa mới: Phòng " + roomNum;
            String content = String.format("Sinh viên %s vừa gửi yêu cầu sửa chữa tại phòng %s. Mã yêu cầu: %s. Hạng mục: %s. Mức độ: %s. Mô tả: %s",
                    reporterName,
                    roomNum,
                    maintenance.getRequestCode(),
                    request.getCategory() != null ? request.getCategory() : "Cơ sở vật chất",
                    priority,
                    request.getDescription());
            notificationService.notifyStaffAndAdmins(title, content, reporter, maintenance.getRequestCode());
        }

        return maintenanceMapper.toResponse(maintenance);
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
        Room activeStudentRoom = null;
        if (studentRepository != null && contractRepository != null) {
            Optional<Student> studentOpt = studentRepository.findByUser_UserId(reporter.getUserId());
            if (studentOpt.isPresent()) {
                List<Contract> contracts = contractRepository.findByStudent_StudentId(studentOpt.get().getStudentId());
                for (Contract c : contracts) {
                    if ("ACTIVE".equalsIgnoreCase(c.getStatus()) && c.getBed() != null && c.getBed().getRoom() != null) {
                        activeStudentRoom = c.getBed().getRoom();
                        break;
                    }
                }
            }
        }

        boolean isStudent = currentUserService != null && currentUserService.hasRole("STUDENT");
        if (isStudent) {
            if (activeStudentRoom == null) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên phải có hợp đồng nội trú ACTIVE để yêu cầu sửa chữa");
            }
            if (roomId != null && !roomId.isBlank() && !roomId.equals(activeStudentRoom.getRoomId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên chỉ được yêu cầu sửa chữa cho đúng phòng mình đang ở");
            }
            return activeStudentRoom;
        }

        if (roomId != null && !roomId.isBlank()) {
            return roomRepository.findById(roomId.trim())
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        }

        if (activeStudentRoom != null) {
            return activeStudentRoom;
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
        MaintenanceRequest maintenance = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentUserId = currentUserService.userId();
            if (maintenance.getReportedBy() == null || !maintenance.getReportedBy().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền truy cập yêu cầu bảo trì của người khác");
            }
        }

        return maintenanceMapper.toResponse(maintenance);
    }

    // Lấy danh sách yêu cầu bảo trì theo phòng
    @Transactional(readOnly = true)
    public List<MaintenanceRequest_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String studentId = currentUserService.studentId();
            List<Contract> activeContracts = contractRepository.findByStudent_StudentIdAndStatus(studentId, "ACTIVE");
            boolean livesInRoom = activeContracts.stream()
                    .anyMatch(c -> c.getBed() != null && c.getBed().getRoom() != null && roomId.equals(c.getBed().getRoom().getRoomId()));
            if (!livesInRoom) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn chỉ có thể xem yêu cầu bảo trì của phòng bạn đang ở");
            }
        }

        List<MaintenanceRequest> list = maintenanceRepository.findByRoom_RoomId(roomId);
        return maintenanceMapper.toResponseList(list);
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

        Page<MaintenanceRequest> pageResult = maintenanceRepository.searchMaintenanceRequests(kw, st, pr, rn, cat, pageable);
        List<MaintenanceRequest_Response> data = maintenanceMapper.toResponseList(pageResult.getContent());

        return Page_Response.<MaintenanceRequest_Response>builder()
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

    // Phân công nhân viên phụ trách xử lý yêu cầu bảo trì
    @Transactional
    public MaintenanceRequest_Response assignStaff(String requestId, String staffId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền phân công nhân viên sửa chữa");
        }
        MaintenanceRequest maintenance = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if ("COMPLETED".equalsIgnoreCase(maintenance.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân công cho yêu cầu bảo trì đã hoàn thành");
        }
        if ("CANCELLED".equalsIgnoreCase(maintenance.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân công cho yêu cầu bảo trì đã bị hủy");
        }

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        maintenance.setAssignedTo(staff);
        maintenance.setStatus("IN_PROGRESS");
        maintenance = maintenanceRepository.save(maintenance);

        return maintenanceMapper.toResponse(maintenance);
    }

    // Hoàn thành xử lý yêu cầu bảo trì
    @Transactional
    public MaintenanceRequest_Response complete(String requestId, String resolutionNote) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xác nhận hoàn thành sửa chữa");
        }
        MaintenanceRequest maintenance = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (maintenance.getAssignedTo() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Yêu cầu bảo trì phải được phân công nhân sự trước khi hoàn thành");
        }
        if ("COMPLETED".equalsIgnoreCase(maintenance.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Yêu cầu bảo trì đã được hoàn thành trước đó");
        }
        if ("CANCELLED".equalsIgnoreCase(maintenance.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể hoàn thành yêu cầu bảo trì đã bị hủy");
        }

        maintenance.setStatus("COMPLETED");
        maintenance.setCompletedDate(LocalDateTime.now());
        maintenance.setResolutionNote(resolutionNote);
        maintenance = maintenanceRepository.save(maintenance);

        return maintenanceMapper.toResponse(maintenance);
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
        MaintenanceRequest maintenance = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        if (!"PENDING".equalsIgnoreCase(maintenance.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể hủy yêu cầu bảo trì khi đang ở trạng thái PENDING");
        }

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentUserId = currentUserService.userId();
            if (maintenance.getReportedBy() == null || !maintenance.getReportedBy().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy yêu cầu bảo trì của người khác");
            }
        }

        maintenance.setStatus("CANCELLED");
        maintenance.setResolutionNote((reason != null && !reason.isBlank()) ? reason.trim() : "Người dùng đã hủy yêu cầu");
        maintenance = maintenanceRepository.save(maintenance);

        return maintenanceMapper.toResponse(maintenance);
    }

    // Xóa yêu cầu bảo trì
    @Transactional
    public void delete(String requestId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xóa yêu cầu sửa chữa");
        }
        MaintenanceRequest maintenance = maintenanceRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo trì"));

        maintenanceRepository.delete(maintenance);
    }
}
