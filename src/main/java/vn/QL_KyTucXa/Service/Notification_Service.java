package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Notification_Request;
import vn.QL_KyTucXa.DTO.Response.Notification_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Notification;
import vn.QL_KyTucXa.Entity.NotificationRecipient;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Notification_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.NotificationRecipient_Repository;
import vn.QL_KyTucXa.Repository.Notification_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Notification_Service {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "notificationId", "title", "targetType", "isPublished", "publishedAt", "createdAt");

    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of(
            "ALL", "USER", "STUDENT", "STAFF", "ROOM", "FLOOR", "BUILDING");

    private static final Set<String> REQUIRE_TARGET_ID_TYPES = Set.of(
            "USER", "ROOM", "FLOOR", "BUILDING");

    Notification_Repository notificationRepository;
    NotificationRecipient_Repository notificationRecipientRepository;
    User_Repository userRepository;
    Student_Repository studentRepository;
    Staff_Repository staffRepository;
    Contract_Repository contractRepository;
    CurrentUser_Service currentUserService;
    Notification_Mapper notificationMapper;

    // Tạo thông báo mới và tự động phân phát đến người nhận nếu phát hành ngay
    @Transactional
    public Notification_Response create(Notification_Request request) {
        log.info("Tạo thông báo mới: {}", request.getTitle());

        if (currentUserService == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String creatorId;
        try {
            creatorId = currentUserService.userId();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (creatorId == null || creatorId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User nguoi_dung = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Notification thong_bao = notificationMapper.toEntity(request);
        thong_bao.setCreatedBy(nguoi_dung);
        String targetType = (thong_bao.getTargetType() == null || thong_bao.getTargetType().isBlank())
                ? "ALL"
                : thong_bao.getTargetType().trim().toUpperCase();
        String targetId = (request.getTargetId() != null && !request.getTargetId().isBlank())
                ? request.getTargetId().trim()
                : null;

        validateTarget(targetType, targetId);
        thong_bao.setTargetType(targetType);
        thong_bao.setTargetId(targetId);

        boolean isPub = Boolean.TRUE.equals(request.getIsPublished());
        thong_bao.setIsPublished(isPub);
        if (isPub) {
            thong_bao.setPublishedAt(LocalDateTime.now());
        } else {
            thong_bao.setPublishedAt(null);
        }

        thong_bao = notificationRepository.save(thong_bao);

        // Tạo người nhận thông báo nếu đã phát hành ngay lúc tạo
        if (isPub) {
            createRecipientsForNotification(thong_bao);
        }

        return notificationMapper.toResponse(thong_bao);
    }

    // Lấy thông tin chi tiết thông báo theo ID
    @Transactional(readOnly = true)
    public Notification_Response getById(String notificationId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Sinh viên chỉ được xem thông báo qua Hộp thư (/notifications/inbox)");
        }

        Notification thong_bao = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        return notificationMapper.toResponse(thong_bao);
    }

    // Lấy danh sách thông báo đã phát hành đại chúng (targetType = ALL) có phân
    // trang
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getPublishedNotifications(int page, int size, String sortBy,
            String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "publishedAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Notification> trang_thong_bao = notificationRepository.findByIsPublishedAndTargetType(true, "ALL",
                pageable);
        List<Notification_Response> danh_sach_ket_qua = notificationMapper.toResponseList(trang_thong_bao.getContent());

        return Page_Response.<Notification_Response>builder()
                .currentPage(trang_thong_bao.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_thong_bao.getTotalPages())
                .totalElements(trang_thong_bao.getTotalElements())
                .hasPrevious(trang_thong_bao.hasPrevious())
                .hasNext(trang_thong_bao.hasNext())
                .isFirst(trang_thong_bao.isFirst())
                .isLast(trang_thong_bao.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Lấy danh sách thông báo đã phát hành đại chúng (targetType = ALL) không phân
    // trang
    @Transactional(readOnly = true)
    public List<Notification_Response> getPublishedNotifications() {
        List<Notification> danh_sach_thong_bao = notificationRepository.findByIsPublishedAndTargetType(true, "ALL");
        return notificationMapper.toResponseList(danh_sach_thong_bao);
    }

    // Lấy danh sách tất cả thông báo phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và phân trang danh sách thông báo theo tiêu đề và trạng thái phát
    // hành
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getAll(
            int page,
            int size,
            String title,
            Boolean isPublished,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Sinh viên chỉ được xem thông báo qua Hộp thư (/notifications/inbox)");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String titleFilter = (title != null && !title.isBlank()) ? title.trim() : null;

        Page<Notification> trang_thong_bao = notificationRepository.searchNotifications(titleFilter, isPublished,
                pageable);
        List<Notification_Response> danh_sach_ket_qua = notificationMapper.toResponseList(trang_thong_bao.getContent());

        return Page_Response.<Notification_Response>builder()
                .currentPage(trang_thong_bao.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_thong_bao.getTotalPages())
                .totalElements(trang_thong_bao.getTotalElements())
                .hasPrevious(trang_thong_bao.hasPrevious())
                .hasNext(trang_thong_bao.hasNext())
                .isFirst(trang_thong_bao.isFirst())
                .isLast(trang_thong_bao.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Cập nhật thông báo chỉ cho phép khi thông báo chưa được phát hành
    @Transactional
    public Notification_Response update(String notificationId, Notification_Request request) {
        log.info("Cập nhật thông báo có ID: {}", notificationId);

        Notification thong_bao = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (Boolean.TRUE.equals(thong_bao.getIsPublished())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể cập nhật thông báo đã phát hành");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            thong_bao.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            thong_bao.setContent(request.getContent().trim());
        }

        String targetType = thong_bao.getTargetType();
        if (request.getTargetType() != null && !request.getTargetType().isBlank()) {
            targetType = request.getTargetType().trim().toUpperCase();
        }
        String targetId = thong_bao.getTargetId();
        if (request.getTargetId() != null) {
            targetId = request.getTargetId().isBlank() ? null : request.getTargetId().trim();
        }

        validateTarget(targetType, targetId);
        thong_bao.setTargetType(targetType);
        thong_bao.setTargetId(targetId);

        boolean isPub = Boolean.TRUE.equals(request.getIsPublished());
        if (isPub) {
            thong_bao.setIsPublished(true);
            thong_bao.setPublishedAt(LocalDateTime.now());
        }

        thong_bao = notificationRepository.save(thong_bao);

        if (isPub) {
            createRecipientsForNotification(thong_bao);
        }

        return notificationMapper.toResponse(thong_bao);
    }

    // Phát hành thông báo và phân phối đến hộp thư người nhận
    @Transactional
    public Notification_Response publish(String notificationId) {
        Notification thong_bao = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (Boolean.TRUE.equals(thong_bao.getIsPublished())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Thông báo này đã được phát hành trước đó");
        }

        thong_bao.setIsPublished(true);
        thong_bao.setPublishedAt(LocalDateTime.now());
        thong_bao = notificationRepository.save(thong_bao);

        createRecipientsForNotification(thong_bao);

        return notificationMapper.toResponse(thong_bao);
    }

    // Xóa thông báo và xóa tất cả người nhận liên quan trực tiếp bằng 1 câu lệnh native SQL
    @Transactional
    public void delete(String notificationId) {
        Notification thong_bao = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (notificationRecipientRepository != null) {
            int deletedRecipients = notificationRecipientRepository.deleteRecipientsByNotificationId(notificationId);
            log.info("Đã xóa siêu tốc {} người nhận của thông báo ID={}", deletedRecipients, notificationId);
        }

        notificationRepository.delete(thong_bao);
    }

    // Tạo danh sách người nhận thông báo theo loại đối tượng targetType và targetId
    private void createRecipientsForNotification(Notification notification) {
        if (notification == null || notification.getNotificationId() == null || notificationRecipientRepository == null) {
            return;
        }

        String notificationId = notification.getNotificationId();
        String targetType = notification.getTargetType() != null ? notification.getTargetType().trim().toUpperCase() : "ALL";
        String targetId = notification.getTargetId() != null ? notification.getTargetId().trim() : null;

        // Tối ưu hóa vượt bậc: Thực hiện Bulk Native Insert trực tiếp trên Database cho các nhóm lớn
        // Giúp gửi cho 1.600 sinh viên trong ~200ms thay vì gửi 1.600 câu INSERT qua mạng gây nghẽn/quay vô tận
        if ("STUDENT".equals(targetType) && (targetId == null || targetId.isBlank())) {
            int inserted = notificationRecipientRepository.insertRecipientsForStudents(notificationId);
            log.info("Đã tạo {} bản ghi người nhận (STUDENT) cho thông báo ID={}", inserted, notificationId);
            return;
        }

        if ("ALL".equals(targetType)) {
            int inserted = notificationRecipientRepository.insertRecipientsForAll(notificationId);
            log.info("Đã tạo {} bản ghi người nhận (ALL) cho thông báo ID={}", inserted, notificationId);
            return;
        }

        if ("STAFF".equals(targetType) && (targetId == null || targetId.isBlank())) {
            int inserted = notificationRecipientRepository.insertRecipientsForStaff(notificationId);
            log.info("Đã tạo {} bản ghi người nhận (STAFF) cho thông báo ID={}", inserted, notificationId);
            return;
        }

        // Với các nhóm mục tiêu cụ thể (ROOM, FLOOR, BUILDING, USER), gom danh sách userId và thực thi 1 bulk query
        Set<String> recipientUserIds = new HashSet<>();

        switch (targetType) {
            case "USER":
                if (targetId != null && userRepository != null) {
                    userRepository.findById(targetId)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .ifPresentOrElse(u -> recipientUserIds.add(u.getUserId()), () -> {
                                if (studentRepository != null) {
                                    studentRepository.findById(targetId)
                                            .map(Student::getUser)
                                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                            .ifPresent(u -> recipientUserIds.add(u.getUserId()));
                                }
                            });
                }
                break;

            case "STUDENT": // cụ thể 1 sinh viên
                if (targetId != null && studentRepository != null) {
                    studentRepository.findById(targetId)
                            .map(Student::getUser)
                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .ifPresentOrElse(u -> recipientUserIds.add(u.getUserId()), () -> {
                                studentRepository.findByUser_UserId(targetId)
                                        .map(Student::getUser)
                                        .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                        .ifPresent(u -> recipientUserIds.add(u.getUserId()));
                            });
                }
                break;

            case "STAFF": // cụ thể 1 nhân sự
                if (targetId != null && staffRepository != null) {
                    try {
                        staffRepository.findById(targetId)
                                .map(Staff::getUser)
                                .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .ifPresent(u -> recipientUserIds.add(u.getUserId()));
                    } catch (Exception ignored) {
                    }
                }
                break;

            case "ROOM":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsWithStudentAndUserByRoomId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .map(User::getUserId)
                            .forEach(recipientUserIds::add);
                }
                break;

            case "BUILDING":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByBuildingId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .map(User::getUserId)
                            .forEach(recipientUserIds::add);
                }
                break;

            case "FLOOR":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByFloorId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .map(User::getUserId)
                            .forEach(recipientUserIds::add);
                }
                break;

            default:
                log.warn("Bỏ qua phân phối: Loại đối tượng nhận thông báo không hợp lệ '{}'", targetType);
                break;
        }

        if (!recipientUserIds.isEmpty()) {
            int inserted = notificationRecipientRepository.insertRecipientsForUserIds(notificationId, recipientUserIds);
            log.info("Đã tạo {} bản ghi người nhận cho thông báo ID={}", inserted, notificationId);
        }
    }

    private void validateTarget(String targetType, String targetId) {
        if (targetType == null || !ALLOWED_TARGET_TYPES.contains(targetType)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Loại đối tượng nhận thông báo '" + targetType + "' không hợp lệ. Cho phép: "
                            + ALLOWED_TARGET_TYPES);
        }
        if (REQUIRE_TARGET_ID_TYPES.contains(targetType) && (targetId == null || targetId.isBlank())) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "Mục tiêu nhận thông báo '" + targetType + "' bắt buộc phải cung cấp targetId");
        }
    }

    // Gửi thông báo hệ thống tự động tới toàn bộ Ban Quản lý và Cán bộ / Nhân viên
    // KTX
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStaffAndAdmins(String title, String content, User actor) {
        notifyStaffAndAdmins(title, content, actor, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStaffAndAdmins(String title, String content, User actor, String targetId) {
        try {
            User managedActor = null;
            String actorId = null;
            if (actor != null) {
                try {
                    actorId = actor.getUserId();
                } catch (Exception ignored) {
                }
            }
            if (actorId != null && userRepository != null) {
                try {
                    managedActor = userRepository.findById(actorId).orElse(null);
                } catch (Exception ignored) {
                }
            }
            if (managedActor == null && userRepository != null) {
                managedActor = userRepository.findActiveStaffAndAdmins().stream()
                        .findFirst()
                        .orElse(null);
            }
            if (managedActor == null) {
                log.warn("notifyStaffAndAdmins: Không tìm thấy tài khoản quản trị để phát thông báo");
                return;
            }

            Notification thong_bao = Notification.builder()
                    .title(title)
                    .content(content)
                    .targetType("STAFF")
                    .targetId(null)
                    .createdBy(managedActor)
                    .isPublished(true)
                    .publishedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            thong_bao = notificationRepository.saveAndFlush(thong_bao);
            createRecipientsForNotification(thong_bao);
            log.info("Đã tạo thông báo hệ thống cho Staff/Admin: {}", title);
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo hệ thống tới Staff/Admin: {}", e.getMessage(), e);
        }
    }

    // Gửi thông báo hệ thống trực tiếp cho một sinh viên cụ thể
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStudent(Student student, String title, String content, User actor) {
        try {
            if (student == null)
                return;
            String studentUserId = null;
            try {
                if (student.getUser() != null) {
                    studentUserId = student.getUser().getUserId();
                }
            } catch (Exception ignored) {
            }
            if (studentUserId == null && student.getStudentId() != null && studentRepository != null) {
                try {
                    studentUserId = studentRepository.findById(student.getStudentId())
                            .map(s -> {
                                try {
                                    return s.getUser() != null ? s.getUser().getUserId() : null;
                                } catch (Exception ex) {
                                    return null;
                                }
                            })
                            .orElse(null);
                } catch (Exception ignored) {
                }
            }
            if (studentUserId == null || userRepository == null)
                return;

            User nguoi_dung_sinh_vien = userRepository.findById(studentUserId).orElse(null);
            if (nguoi_dung_sinh_vien == null || !"ACTIVE".equalsIgnoreCase(nguoi_dung_sinh_vien.getStatus()))
                return;

            User managedActor = null;
            String actorId = null;
            if (actor != null) {
                try {
                    actorId = actor.getUserId();
                } catch (Exception ignored) {
                }
            }
            if (actorId != null) {
                try {
                    managedActor = userRepository.findById(actorId).orElse(null);
                } catch (Exception ignored) {
                }
            }
            if (managedActor == null) {
                managedActor = userRepository.findActiveStaffAndAdmins().stream()
                        .findFirst()
                        .orElse(nguoi_dung_sinh_vien);
            }
            if (managedActor == null) {
                managedActor = nguoi_dung_sinh_vien;
            }

            Notification thong_bao = Notification.builder()
                    .title(title)
                    .content(content)
                    .targetType("USER")
                    .targetId(nguoi_dung_sinh_vien.getUserId())
                    .createdBy(managedActor)
                    .isPublished(true)
                    .publishedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            thong_bao = notificationRepository.saveAndFlush(thong_bao);

            NotificationRecipient nguoi_nhan = NotificationRecipient.builder()
                    .notification(thong_bao)
                    .user(nguoi_dung_sinh_vien)
                    .isRead(false)
                    .readAt(null)
                    .build();

            notificationRecipientRepository.saveAndFlush(nguoi_nhan);
            log.info("Đã gửi thông báo tới sinh viên {}: {}", student.getFullName(), title);
        } catch (Exception e) {
            log.error("Không thể gửi thông báo tới sinh viên: {}", e.getMessage(), e);
        }
    }
}
