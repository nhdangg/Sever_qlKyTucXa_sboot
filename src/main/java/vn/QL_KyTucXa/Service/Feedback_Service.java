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
import vn.QL_KyTucXa.DTO.Request.Feedback_Request;
import vn.QL_KyTucXa.DTO.Response.Feedback_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Feedback;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Feedback_Mapper;
import vn.QL_KyTucXa.Repository.Feedback_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Feedback_Service {

    Feedback_Repository feedbackRepository;
    Student_Repository studentRepository;
    Staff_Repository staffRepository;
    CurrentUser_Service currentUserService;
    Feedback_Mapper feedbackMapper;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    static Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "feedbackId", "category", "rating", "status", "respondedAt", "createdAt");

    // Gửi phản hồi mới từ sinh viên đã đăng nhập và khởi tạo ở trạng thái SUBMITTED
    @Transactional
    public Feedback_Response create(Feedback_Request request) {
        log.info("Gửi phản hồi mới từ người dùng");

        Student sinh_vien = resolveStudent(request.getStudentId());

        Feedback phan_hoi = feedbackMapper.toEntity(request);
        phan_hoi.setStudent(sinh_vien);
        phan_hoi.setStatus("SUBMITTED");
        phan_hoi.setResponse(null);
        phan_hoi.setRespondedBy(null);
        phan_hoi.setRespondedAt(null);

        phan_hoi = feedbackRepository.save(phan_hoi);

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null && sinh_vien != null) {
            String studentName = (sinh_vien.getFullName() != null && !sinh_vien.getFullName().isBlank())
                    ? sinh_vien.getFullName() : "Sinh viên";
            String title = "Ý kiến phản ánh mới từ SV " + studentName;
            String content = String.format("Sinh viên %s vừa gửi phản ánh/đánh giá. Danh mục: %s. Đánh giá: %s sao. Nội dung: %s",
                    studentName,
                    phan_hoi.getCategory() != null ? phan_hoi.getCategory() : "Góp ý chung",
                    phan_hoi.getRating() != null ? phan_hoi.getRating() : 5,
                    phan_hoi.getContent() != null ? phan_hoi.getContent() : "");
            notificationService.notifyStaffAndAdmins(title, content, sinh_vien.getUser(), phan_hoi.getFeedbackId());
        }

        return feedbackMapper.toResponse(phan_hoi);
    }

    // Xác định sinh viên gửi phản hồi ưu tiên lấy từ JWT đăng nhập
    private Student resolveStudent(String requestStudentId) {
        String studentId = null;
        if (currentUserService.hasRole("STUDENT")) {
            studentId = currentUserService.studentId();
        } else if (requestStudentId != null && !requestStudentId.isBlank()) {
            studentId = requestStudentId;
        } else {
            studentId = currentUserService.findStudentId()
                    .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        }

        return studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Lấy chi tiết phản hồi theo ID có kiểm tra quyền sở hữu đối với sinh viên
    @Transactional(readOnly = true)
    public Feedback_Response getById(String feedbackId) {
        Feedback phan_hoi = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phản hồi"));

        checkOwnership(phan_hoi);

        return feedbackMapper.toResponse(phan_hoi);
    }

    // Lấy danh sách phản hồi của sinh viên có kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public List<Feedback_Response> getByStudentId(String studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        // Sinh viên chỉ được phép xem phản hồi của chính mình
        if (currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.studentId();
            if (!currentStudentId.equals(studentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem phản hồi của sinh viên khác");
            }
        }

        List<Feedback> danh_sach_phan_hoi = feedbackRepository.findByStudent_StudentId(studentId);
        return feedbackMapper.toResponseList(danh_sach_phan_hoi);
    }

    // Kiểm tra quyền sở hữu phản hồi: sinh viên chỉ được xem phản hồi của chính
    // mình
    private void checkOwnership(Feedback feedback) {
        if (currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.studentId();
            if (feedback.getStudent() == null || !currentStudentId.equals(feedback.getStudent().getStudentId())) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem phản hồi này");
            }
        }
    }

    // Lấy danh sách phản hồi phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Feedback_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, sortBy, sortDir);
    }

    // Lấy danh sách phản hồi có tìm kiếm theo từ khóa trạng thái danh mục và phân
    // trang
    @Transactional(readOnly = true)
    public Page_Response<Feedback_Response> getAll(
            int page,
            int size,
            String keyword,
            String status,
            String category,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xem toàn bộ phản hồi của hệ thống");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String st = (status != null && !status.isBlank()) ? status.trim() : null;
        String cat = (category != null && !category.isBlank()) ? category.trim() : null;

        Page<Feedback> trang_phan_hoi = feedbackRepository.searchFeedbacks(kw, st, cat, pageable);
        List<Feedback_Response> danh_sach_ket_qua = feedbackMapper.toResponseList(trang_phan_hoi.getContent());

        return Page_Response.<Feedback_Response>builder()
                .currentPage(trang_phan_hoi.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_phan_hoi.getTotalPages())
                .totalElements(trang_phan_hoi.getTotalElements())
                .hasPrevious(trang_phan_hoi.hasPrevious())
                .hasNext(trang_phan_hoi.hasNext())
                .isFirst(trang_phan_hoi.isFirst())
                .isLast(trang_phan_hoi.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Trả lời phản hồi của sinh viên và chuyển trạng thái sang RESOLVED
    @Transactional
    public Feedback_Response respond(String feedbackId, String staffId, String responseText) {
        Feedback phan_hoi = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phản hồi"));

        // Chặn trả lời lại nếu phản hồi đã được xử lý
        if ("RESOLVED".equalsIgnoreCase(phan_hoi.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Phản hồi này đã được trả lời trước đó");
        }

        if (responseText == null || responseText.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Nội dung trả lời không được để trống");
        }

        // Lấy nhân viên trả lời ưu tiên từ JWT
        Staff nhan_vien = resolveStaff(staffId);

        phan_hoi.setResponse(responseText.trim());
        if (nhan_vien != null) {
            phan_hoi.setRespondedBy(nhan_vien);
        }
        phan_hoi.setRespondedAt(LocalDateTime.now());
        phan_hoi.setStatus("RESOLVED");

        phan_hoi = feedbackRepository.save(phan_hoi);

        // Bắn thông báo cho sinh viên khi phản ánh được trả lời
        if (notificationService != null && phan_hoi.getStudent() != null) {
            String title = "Phản ánh của bạn đã được phản hồi";
            String content = String.format("Ban Quản lý đã trả lời phản ánh '%s' của bạn: %s",
                    phan_hoi.getCategory() != null ? phan_hoi.getCategory() : "Ý kiến KTX",
                    phan_hoi.getResponse());
            notificationService.notifyStudent(phan_hoi.getStudent(), title, content, nhan_vien != null ? nhan_vien.getUser() : null);
        }

        return feedbackMapper.toResponse(phan_hoi);
    }

    // Xác định nhân viên trả lời phản hồi: lấy từ JWT, chỉ ADMIN mới được chỉ định
    // staffId khác
    private Staff resolveStaff(String staffId) {
        if (currentUserService != null && currentUserService.hasRole("ADMIN") && staffId != null
                && !staffId.isBlank()) {
            return staffRepository.findById(staffId.trim())
                    .orElse(null);
        }
        if (currentUserService != null) {
            Optional<String> currentStaffId = currentUserService.findStaffId();
            if (currentStaffId.isPresent()) {
                return staffRepository.findById(currentStaffId.get()).orElse(null);
            }
            if (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")) {
                return null;
            }
        }
        throw new AppException(ErrorCode.UNAUTHORIZED,
                "Chỉ cán bộ nhân viên hoặc quản trị viên mới có quyền trả lời phản hồi");
    }

    // Xóa phản hồi theo ID có kiểm tra quyền sở hữu đối với tài khoản sinh viên
    @Transactional
    public void delete(String feedbackId) {
        Feedback phan_hoi = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phản hồi"));

        boolean isStaffOrAdmin = currentUserService != null
                && (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER")
                        || currentUserService.hasRole("STAFF"));
        if (!isStaffOrAdmin) {
            checkOwnership(phan_hoi);
            if ("RESOLVED".equalsIgnoreCase(phan_hoi.getStatus()) || phan_hoi.getResponse() != null) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phản hồi đã được xử lý");
            }
        }

        feedbackRepository.delete(phan_hoi);
    }
}
