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
import vn.QL_KyTucXa.DTO.Request.Visitor_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Visitor_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.Visitor;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Visitor_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.Visitor_Repository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Visitor_Service {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "visitorId", "visitorName", "checkInTime", "checkOutTime"
    );

    Visitor_Repository visitorRepository;
    Student_Repository studentRepository;
    Staff_Repository staffRepository;
    Contract_Repository contractRepository;
    CurrentUser_Service currentUserService;
    SystemSetting_Service systemSettingService;
    Visitor_Mapper visitorMapper;

    @lombok.experimental.NonFinal
    Set<String> warnedOverdueVisitorIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    // Kiểm tra quy định khung giờ đón tiếp khách thăm KTX
    private void validateVisitingHours(LocalDateTime checkInTime) {
        String maxVisitHourStr = systemSettingService != null
                ? systemSettingService.getSettingValue("MAX_VISIT_HOUR", "21:30")
                : "21:30";
        LocalTime maxVisit;
        try {
            maxVisit = LocalTime.parse(maxVisitHourStr);
        } catch (Exception ex) {
            log.warn("Lỗi phân tích cú pháp MAX_VISIT_HOUR='{}', sử dụng mặc định 21:30", maxVisitHourStr);
            maxVisit = LocalTime.of(21, 30);
        }
        LocalTime startVisit = LocalTime.of(7, 0);
        LocalTime checkInLocalTime = checkInTime.toLocalTime();

        if (checkInLocalTime.isAfter(maxVisit) || checkInLocalTime.isBefore(startVisit)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Ngoài khung giờ cho phép đón khách thăm KTX (từ 07:00 đến " + maxVisitHourStr + ")");
        }
    }

    // Tra cứu sinh viên đón tiếp: lấy từ JWT nếu là sinh viên
    private Student resolveStudent(String hostStudentId) {
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (isStaffOrAdmin && hostStudentId != null && !hostStudentId.isBlank()) {
            return studentRepository.findById(hostStudentId)
                    .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        }

        return currentUserService.findStudentId()
                .flatMap(studentRepository::findById)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Tra cứu phòng đón tiếp: sinh viên chỉ được đón khách tại phòng mình đang có hợp đồng ACTIVE
    private Room resolveRoom(String requestedRoomId, Student student) {
        List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(),
                "ACTIVE");
        if (danh_sach_hop_dong_hieu_luc.isEmpty()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên đón tiếp hiện không có hợp đồng nội trú hiệu lực (ACTIVE)");
        }

        Room phong_sinh_vien = null;
        for (Contract hop_dong : danh_sach_hop_dong_hieu_luc) {
            if (hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                phong_sinh_vien = hop_dong.getBed().getRoom();
                break;
            }
        }

        if (phong_sinh_vien == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND, "Không xác định được phòng ở hiện tại của sinh viên");
        }

        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin && requestedRoomId != null && !requestedRoomId.isBlank()
                && !requestedRoomId.equals(phong_sinh_vien.getRoomId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên chỉ được đón khách thăm tại đúng phòng mình đang ở");
        }

        return phong_sinh_vien;
    }

    // Đăng ký và tiếp nhận khách vào thăm ký túc xá
    @Transactional
    public Visitor_Response checkIn(Visitor_Request request) {
        log.info("Khách vào thăm: {}, Sinh viên đón: {}", request.getVisitorName(), request.getHostStudentId());

        // Bắt buộc CCCD của khách thăm
        if (request.getNationalId() == null || request.getNationalId().isBlank()) {
            throw new AppException(ErrorCode.NATIONAL_ID_BLANK, "Số CCCD của khách thăm không được để trống");
        }
        String cleanNid = request.getNationalId().trim();

        Student sinh_vien_don = resolveStudent(request.getHostStudentId());
        Room phong = resolveRoom(request.getRoomId(), sinh_vien_don);

        // Kiểm tra khách cùng số CCCD hiện đang ở trong KTX chưa check-out
        if (visitorRepository.existsByNationalIdAndCheckOutTimeIsNull(cleanNid)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Khách có số CCCD '" + cleanNid + "' hiện đang có mặt trong KTX và chưa thực hiện check-out");
        }

        // Kiểm tra giới hạn số lượng khách cùng lúc của sinh viên và phòng (chỉ tính khách đang thực tế ở trong KTX)
        int maxPerStudent = systemSettingService != null
                ? systemSettingService.getIntSetting("MAX_CONCURRENT_VISITORS_PER_STUDENT", 2)
                : 2;
        int maxPerRoom = systemSettingService != null
                ? systemSettingService.getIntSetting("MAX_CONCURRENT_VISITORS_PER_ROOM", 4)
                : 4;

        long currentForStudent = visitorRepository.countActiveVisitorsByStudentId(sinh_vien_don.getStudentId());
        if (currentForStudent >= maxPerStudent) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên đang đón tối đa " + maxPerStudent + " khách thăm cùng lúc");
        }

        long currentForRoom = visitorRepository.countActiveVisitorsByRoomId(phong.getRoomId());
        if (currentForRoom >= maxPerRoom) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phòng " + phong.getRoomNumber() + " đang đón tối đa " + maxPerRoom + " khách thăm cùng lúc");
        }

        // Xác định thời gian và vai trò check-in
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        LocalDateTime checkInTime;
        Staff nguoi_duyet = null;
        String purposePrefix = "";

        if (!isStaffOrAdmin) {
            // Sinh viên đăng ký online: trạng thái là PENDING, checkInTime lưu thời điểm nộp đơn
            checkInTime = LocalDateTime.now();
            validateVisitingHours(checkInTime);
            purposePrefix = "[PENDING] ";
        } else {
            // Cán bộ/bảo vệ/Admin tiếp nhận trực tiếp tại cổng: vào KTX ngay (IN_DORM)
            if (request.getCheckInTime() != null) {
                if (request.getCheckInTime().isAfter(LocalDateTime.now())) {
                    throw new AppException(ErrorCode.OPERATION_FAILED, "Thời gian vào thăm không thể ở tương lai");
                }
                checkInTime = request.getCheckInTime();
            } else {
                checkInTime = LocalDateTime.now();
            }

            try {
                validateVisitingHours(checkInTime);
            } catch (AppException e) {
                log.info("Cán bộ/bảo vệ ghi nhận khách thăm ngoài khung giờ quy định: {}", checkInTime);
            }

            if (request.getApprovedBy() != null && !request.getApprovedBy().isBlank()) {
                nguoi_duyet = staffRepository.findById(request.getApprovedBy())
                        .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
            } else {
                nguoi_duyet = currentUserService.findStaffId()
                        .flatMap(staffRepository::findById)
                        .orElse(null);
            }
        }

        Visitor khach_tham = visitorMapper.toEntity(request);
        khach_tham.setHostStudent(sinh_vien_don);
        khach_tham.setRoom(phong);
        khach_tham.setCheckInTime(checkInTime);
        khach_tham.setApprover(nguoi_duyet);
        khach_tham.setNationalId(cleanNid);

        String rawPurpose = (request.getPurpose() != null) ? request.getPurpose().trim() : "";
        String fullPurpose = purposePrefix + rawPurpose;
        if (fullPurpose.length() > 255) {
            fullPurpose = fullPurpose.substring(0, 255);
        }
        khach_tham.setPurpose(fullPurpose);

        khach_tham = visitorRepository.save(khach_tham);

        // Bắn thông báo tự động cho Ban Quản trị / Bảo vệ / Nhân viên KTX nếu sinh viên đăng ký khách
        if (notificationService != null && !isStaffOrAdmin && sinh_vien_don != null) {
            try {
                String maskedCccd = (cleanNid.length() > 6)
                        ? cleanNid.substring(0, 3) + "******" + cleanNid.substring(cleanNid.length() - 3)
                        : "******";
                String title = "Đăng ký khách thăm mới: SV " + sinh_vien_don.getFullName();
                String content = String.format("Sinh viên %s (MSSV: %s, Phòng: %s) vừa đăng ký tiếp khách: %s (CCCD: %s).",
                        sinh_vien_don.getFullName(),
                        sinh_vien_don.getStudentCode(),
                        phong.getRoomNumber(),
                        request.getVisitorName(),
                        maskedCccd);
                notificationService.notifyStaffAndAdmins(title, content, sinh_vien_don.getUser(), khach_tham.getVisitorId());
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo đăng ký khách thăm: {}", ex.getMessage());
            }
        }

        return toSanitizedResponse(khach_tham);
    }

    // Phê duyệt đăng ký khách thăm (chuyển PENDING sang IN_DORM và ghi nhận thời điểm vào cổng thực tế)
    @Transactional
    public Visitor_Response approve(String visitorId) {
        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        if (khach_tham.getCheckOutTime() != null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Khách thăm đã check-out ra về, không thể duyệt lại");
        }

        String currentPurpose = khach_tham.getPurpose() != null ? khach_tham.getPurpose() : "";
        if (!currentPurpose.startsWith("[PENDING]")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Khách thăm đã được phê duyệt vào cổng trước đó hoặc không ở trạng thái chờ duyệt");
        }

        // Kiểm tra khung giờ thăm tại thời điểm duyệt vào cổng
        LocalDateTime now = LocalDateTime.now();
        try {
            validateVisitingHours(now);
        } catch (AppException e) {
            log.warn("Cán bộ/bảo vệ duyệt khách vào cổng ngoài khung giờ quy định: {}", now);
        }

        // Lấy thông tin cán bộ phê duyệt; nếu là tài khoản ADMIN chưa có hồ sơ nhân viên thì cho phép approver = null
        Staff nhan_vien_hien_tai = null;
        if (currentUserService != null) {
            nhan_vien_hien_tai = currentUserService.findStaffId()
                    .flatMap(staffRepository::findById)
                    .orElse(null);
            if (nhan_vien_hien_tai == null && !currentUserService.hasRole("ADMIN")) {
                throw new AppException(ErrorCode.STAFF_NOT_FOUND, "Không tìm thấy hồ sơ nhân viên của cán bộ phê duyệt");
            }
        }

        // Xóa tiền tố [PENDING] để đánh dấu đã vào cổng thực tế
        String cleanedPurpose = currentPurpose.replace("[PENDING] ", "").replace("[PENDING]", "").trim();
        khach_tham.setPurpose(cleanedPurpose);
        khach_tham.setApprover(nhan_vien_hien_tai);
        khach_tham.setCheckInTime(now);
        khach_tham = visitorRepository.save(khach_tham);

        // Bắn thông báo cho sinh viên khi khách được duyệt vào cổng
        if (notificationService != null && khach_tham.getHostStudent() != null) {
            try {
                String title = "Khách thăm của bạn đã được duyệt vào cổng";
                String content = String.format("Khách thăm %s đã được Ban Quản lý / Bảo vệ duyệt vào KTX lúc %s.",
                        khach_tham.getVisitorName(),
                        khach_tham.getCheckInTime());
                notificationService.notifyStudent(khach_tham.getHostStudent(), title, content,
                        nhan_vien_hien_tai != null ? nhan_vien_hien_tai.getUser() : null);
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo duyệt khách thăm cho sinh viên: {}", ex.getMessage());
            }
        }

        return toSanitizedResponse(khach_tham);
    }

    // Từ chối đơn đăng ký khách thăm (chỉ dành cho cán bộ/bảo vệ/admin)
    @Transactional
    public Visitor_Response reject(String visitorId, String reason) {
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ cán bộ hoặc quản trị viên mới có quyền từ chối khách thăm");
        }

        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        if (khach_tham.getCheckOutTime() != null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Khách thăm đã check-out ra về hoặc đơn đã đóng");
        }

        String currentPurpose = khach_tham.getPurpose() != null ? khach_tham.getPurpose() : "";
        if (!currentPurpose.startsWith("[PENDING]")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Khách thăm đã vào ký túc xá, không thể từ chối. Vui lòng thực hiện Check-out khi khách rời KTX");
        }

        LocalDateTime now = LocalDateTime.now();
        khach_tham.setCheckOutTime(now); // Đóng đơn để giải phóng hạn mức khách cùng lúc

        String rawPurpose = currentPurpose.replace("[PENDING] ", "").replace("[PENDING]", "").trim();
        String rejectNote = (reason != null && !reason.isBlank()) ? reason.trim() : "Từ chối tiếp nhận";
        String fullPurpose = "[REJECTED] " + rejectNote + (rawPurpose.isEmpty() ? "" : " - " + rawPurpose);
        if (fullPurpose.length() > 255) {
            fullPurpose = fullPurpose.substring(0, 255);
        }
        khach_tham.setPurpose(fullPurpose);

        khach_tham = visitorRepository.save(khach_tham);

        // Bắn thông báo từ chối cho sinh viên
        if (notificationService != null && khach_tham.getHostStudent() != null) {
            try {
                String title = "Đăng ký khách thăm bị từ chối";
                String content = String.format("Đăng ký đón khách thăm %s của bạn đã bị từ chối. Lý do: %s",
                        khach_tham.getVisitorName(), rejectNote);
                notificationService.notifyStudent(khach_tham.getHostStudent(), title, content, null);
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo từ chối khách thăm: {}", ex.getMessage());
            }
        }

        return toSanitizedResponse(khach_tham);
    }

    // Sinh viên hủy đơn đăng ký khách thăm khi chưa được duyệt
    @Transactional
    public Visitor_Response cancel(String visitorId) {
        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        String currentPurpose = khach_tham.getPurpose() != null ? khach_tham.getPurpose() : "";
        if (!currentPurpose.startsWith("[PENDING]")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Đăng ký khách thăm đã được phê duyệt hoặc đã kết thúc, không thể hủy");
        }

        // Kiểm tra quyền: chỉ chính sinh viên đón tiếp hoặc cán bộ quản lý mới được hủy
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (khach_tham.getHostStudent() == null || !khach_tham.getHostStudent().getStudentId().equals(currentStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy đăng ký khách của sinh viên khác");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        khach_tham.setCheckOutTime(now); // Đóng đơn để giải phóng hạn mức

        String rawPurpose = currentPurpose.replace("[PENDING] ", "").replace("[PENDING]", "").trim();
        String fullPurpose = "[CANCELLED] " + (rawPurpose.isEmpty() ? "Sinh viên đã hủy yêu cầu" : rawPurpose);
        if (fullPurpose.length() > 255) {
            fullPurpose = fullPurpose.substring(0, 255);
        }
        khach_tham.setPurpose(fullPurpose);

        khach_tham = visitorRepository.save(khach_tham);
        return toSanitizedResponse(khach_tham);
    }

    // Xác nhận khách ra về (Check-out)
    @Transactional
    public Visitor_Response checkOut(String visitorId) {
        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        String purpose = khach_tham.getPurpose() != null ? khach_tham.getPurpose() : "";
        if (purpose.startsWith("[PENDING]")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Khách thăm chưa được phê duyệt vào cổng, không thể thực hiện check-out. Vui lòng chọn Hủy hoặc Từ chối đăng ký");
        }

        if (khach_tham.getCheckOutTime() != null) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Khách thăm này đã check-out lúc " + khach_tham.getCheckOutTime());
        }

        // Kiểm tra quyền: chỉ nhân viên/bảo vệ hoặc chính sinh viên đón tiếp mới được check-out
        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF");
        if (!isStaffOrAdmin) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (khach_tham.getHostStudent() == null || !khach_tham.getHostStudent().getStudentId().equals(currentStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền thực hiện check-out cho khách của sinh viên khác");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(khach_tham.getCheckInTime())) {
            now = khach_tham.getCheckInTime();
        }

        khach_tham.setCheckOutTime(now);
        warnedOverdueVisitorIds.remove(visitorId);
        khach_tham = visitorRepository.save(khach_tham);
        return toSanitizedResponse(khach_tham);
    }

    @Transactional(readOnly = true)
    public Visitor_Response getById(String visitorId) {
        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (khach_tham.getHostStudent() == null || !khach_tham.getHostStudent().getStudentId().equals(currentStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập thông tin khách của sinh viên khác");
            }
        }

        return toSanitizedResponse(khach_tham);
    }

    @Transactional(readOnly = true)
    public List<Visitor_Response> getByHostStudentId(String studentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse("");
            if (!currentStudentId.equals(studentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập danh sách khách của sinh viên khác");
            }
        }

        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Visitor> danh_sach_khach = visitorRepository.findByHostStudent_StudentId(studentId);
        return toSanitizedResponseList(danh_sach_khach);
    }

    // Tìm kiếm khách thăm có phân trang
    @Transactional(readOnly = true)
    public Page_Response<Visitor_Response> search(
            String keyword,
            String hostStudentId,
            String roomId,
            String status,
            Boolean currentlyInDorm,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        // Sinh viên gọi search thì bắt buộc ép về studentId của chính mình (chống lộ dữ liệu khách của sinh viên khác)
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            hostStudentId = currentUserService.studentId();
        }

        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "checkInTime";
        int safeSize = (size <= 0) ? 10 : Math.min(size, 100);
        int safePage = Math.max(page, 1);
        int pageIndex = safePage - 1;

        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Visitor> trang_khach = visitorRepository.searchVisitors(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (hostStudentId != null && !hostStudentId.isBlank()) ? hostStudentId.trim() : null,
                (roomId != null && !roomId.isBlank()) ? roomId.trim() : null,
                (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null,
                currentlyInDorm,
                fromDate,
                toDate,
                pageable);

        List<Visitor_Response> danh_sach_ket_qua = toSanitizedResponseList(trang_khach.getContent());

        return Page_Response.<Visitor_Response>builder()
                .currentPage(trang_khach.getNumber() + 1)
                .pageSize(trang_khach.getSize())
                .totalPages(trang_khach.getTotalPages())
                .totalElements(trang_khach.getTotalElements())
                .hasPrevious(trang_khach.hasPrevious())
                .hasNext(trang_khach.hasNext())
                .isFirst(trang_khach.isFirst())
                .isLast(trang_khach.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    @Transactional(readOnly = true)
    public Page_Response<Visitor_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return search(null, null, null, null, null, null, null, page, size, sortBy, sortDir);
    }

    // Tác vụ quét và gửi cảnh báo khách thăm ở quá giờ quy định trong KTX
    @Transactional
    public int scanAndNotifyOverdueVisitors() {
        String maxVisitHourStr = systemSettingService != null
                ? systemSettingService.getSettingValue("MAX_VISIT_HOUR", "21:30")
                : "21:30";
        LocalTime maxVisit;
        try {
            maxVisit = LocalTime.parse(maxVisitHourStr);
        } catch (Exception ex) {
            maxVisit = LocalTime.of(21, 30);
        }

        LocalTime now = LocalTime.now();
        boolean isPastMaxHour = now.isAfter(maxVisit) || now.isBefore(LocalTime.of(5, 0));

        List<Visitor> danh_sach_khach_trong_ktx = visitorRepository.findActiveVisitorsInDorm();
        int overdueCount = 0;

        for (Visitor khach : danh_sach_khach_trong_ktx) {
            boolean isOverdue = isPastMaxHour;
            // Nếu khách vào thăm liên tục quá 4 giờ cũng tính là quá hạn
            if (!isOverdue && khach.getCheckInTime() != null) {
                if (khach.getCheckInTime().plusHours(4).isBefore(LocalDateTime.now())) {
                    isOverdue = true;
                }
            }

            if (isOverdue) {
                overdueCount++;
                // Chống lặp thông báo mỗi 30 phút cho cùng một khách thăm
                if (notificationService != null && !warnedOverdueVisitorIds.contains(khach.getVisitorId())) {
                    try {
                        String title = "Cảnh báo khách thăm quá giờ: " + khach.getVisitorName();
                        String content = String.format("Khách thăm %s (CCCD: %s) tại phòng %s (SV đón: %s) hiện chưa check-out dù đã quá giờ tiếp khách quy định (%s).",
                                khach.getVisitorName(),
                                khach.getNationalId() != null ? khach.getNationalId() : "",
                                khach.getRoom() != null ? khach.getRoom().getRoomNumber() : "",
                                khach.getHostStudent() != null ? khach.getHostStudent().getFullName() : "",
                                maxVisitHourStr);
                        notificationService.notifyStaffAndAdmins(title, content,
                                khach.getHostStudent() != null ? khach.getHostStudent().getUser() : null,
                                khach.getVisitorId());
                        warnedOverdueVisitorIds.add(khach.getVisitorId());
                    } catch (Exception ex) {
                        log.warn("Lỗi gửi thông báo khách quá giờ: {}", ex.getMessage());
                    }
                }
            }
        }

        return overdueCount;
    }

    // Tự động đóng các đơn đăng ký khách thăm PENDING tồn đọng vào cuối ngày
    @Transactional
    public int autoCloseStalePendingVisitors() {
        List<Visitor> tat_ca_khach = visitorRepository.findAll();
        int count = 0;
        for (Visitor khach : tat_ca_khach) {
            String purpose = khach.getPurpose() != null ? khach.getPurpose() : "";
            if (purpose.startsWith("[PENDING]") && khach.getCheckInTime() == null && khach.getCheckOutTime() == null) {
                String raw = purpose.replace("[PENDING]", "").trim();
                khach.setPurpose("[CANCELLED] " + raw + " (Hệ thống tự động hủy do hết ngày chưa duyệt vào cổng)");
                khach.setCheckOutTime(LocalDateTime.now());
                visitorRepository.save(khach);
                count++;
            }
        }
        if (count > 0) {
            log.info("Đã tự động đóng {} đơn đăng ký khách thăm PENDING tồn đọng cuối ngày", count);
        }
        warnedOverdueVisitorIds.clear();
        return count;
    }

    // Bảo vệ dữ liệu cá nhân (che số CCCD và SĐT đối với người dùng không phải cán bộ hoặc sinh viên đón tiếp)
    private Visitor_Response toSanitizedResponse(Visitor visitor, boolean isStaffOrAdmin, String currentStudentId) {
        Visitor_Response ket_qua = visitorMapper.toResponse(visitor);

        String purpose = visitor.getPurpose() != null ? visitor.getPurpose() : "";
        if (purpose.startsWith("[CANCELLED]")) {
            ket_qua.setStatus("CANCELLED");
            ket_qua.setPurpose(purpose.replace("[CANCELLED] ", "").replace("[CANCELLED]", "").trim());
        } else if (purpose.startsWith("[REJECTED]")) {
            ket_qua.setStatus("REJECTED");
            ket_qua.setPurpose(purpose.replace("[REJECTED] ", "").replace("[REJECTED]", "").trim());
        } else if (purpose.startsWith("[PENDING]")) {
            ket_qua.setStatus("PENDING");
            ket_qua.setPurpose(purpose.replace("[PENDING] ", "").replace("[PENDING]", "").trim());
        } else if (visitor.getCheckOutTime() != null) {
            ket_qua.setStatus("CHECKED_OUT");
        } else {
            // Đã vào cổng thực tế trong KTX (kể cả trường hợp ADMIN duyệt nhưng approver = null)
            ket_qua.setStatus("IN_DORM");
        }

        boolean isHost = visitor.getHostStudent() != null
                && visitor.getHostStudent().getStudentId().equals(currentStudentId);

        if (!isStaffOrAdmin && !isHost) {
            if (ket_qua.getNationalId() != null && ket_qua.getNationalId().length() > 6) {
                String nid = ket_qua.getNationalId();
                ket_qua.setNationalId(nid.substring(0, 3) + "******" + nid.substring(nid.length() - 3));
            } else {
                ket_qua.setNationalId("******");
            }

            if (ket_qua.getPhone() != null && ket_qua.getPhone().length() > 5) {
                String ph = ket_qua.getPhone();
                ket_qua.setPhone(ph.substring(0, 3) + "****" + ph.substring(ph.length() - 2));
            } else {
                ket_qua.setPhone("******");
            }

            ket_qua.setIdCardImageUrl(null);
        }

        return ket_qua;
    }

    private Visitor_Response toSanitizedResponse(Visitor visitor) {
        boolean isStaffOrAdmin = currentUserService != null
                && (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                        || currentUserService.hasRole("STAFF"));
        String currentStudentId = (currentUserService != null) ? currentUserService.findStudentId().orElse("") : "";
        return toSanitizedResponse(visitor, isStaffOrAdmin, currentStudentId);
    }

    private List<Visitor_Response> toSanitizedResponseList(List<Visitor> list) {
        if (list == null || list.isEmpty())
            return List.of();
        boolean isStaffOrAdmin = currentUserService != null && (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF"));

        String currentStudentId = (currentUserService != null) ? currentUserService.findStudentId().orElse("") : "";
        return list.stream().map(v -> toSanitizedResponse(v, isStaffOrAdmin, currentStudentId)).toList();
    }

    // Xóa bản ghi khách thăm: chỉ ADMIN hoặc MANAGER mới có quyền xóa để đảm bảo toàn vẹn nhật ký ra vào
    @Transactional
    public void delete(String visitorId) {
        Visitor khach_tham = visitorRepository.findById(visitorId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin khách thăm"));

        boolean isStaffOrAdmin = currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER");
        if (!isStaffOrAdmin) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ quản lý ký túc xá mới có quyền xóa dữ liệu nhật ký khách thăm");
        }

        visitorRepository.delete(khach_tham);
    }
}
