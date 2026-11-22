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

    // Xóa thông báo và xóa tất cả người nhận liên quan trước để tránh lỗi khóa
    // ngoại
    @Transactional
    public void delete(String notificationId) {
        Notification thong_bao = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        List<NotificationRecipient> danh_sach_nguoi_nhan = notificationRecipientRepository
                .findByNotification_NotificationId(notificationId);
        if (danh_sach_nguoi_nhan != null && !danh_sach_nguoi_nhan.isEmpty()) {
            notificationRecipientRepository.deleteAll(danh_sach_nguoi_nhan);
            log.info("Đã xóa {} người nhận trước khi xóa thông báo {}", danh_sach_nguoi_nhan.size(), notificationId);
        }

        notificationRepository.delete(thong_bao);
    }

    // Tạo danh sách người nhận thông báo theo loại đối tượng targetType và targetId
    private void createRecipientsForNotification(Notification notification) {
        if (notification == null || notification.getNotificationId() == null) {
            return;
        }

        Set<User> tap_nguoi_nhan = new HashSet<>();
        String targetType = notification.getTargetType() != null ? notification.getTargetType().trim().toUpperCase()
                : "ALL";
        String targetId = notification.getTargetId() != null ? notification.getTargetId().trim() : null;

        switch (targetType) {
            case "ALL":
                if (userRepository != null) {
                    tap_nguoi_nhan.addAll(userRepository.findByStatus("ACTIVE"));
                }
                break;

            case "USER":
                if (targetId != null && userRepository != null) {
                    userRepository.findById(targetId)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .ifPresent(tap_nguoi_nhan::add);
                }
                break;

            case "STUDENT":
                if (studentRepository != null) {
                    if (targetId != null) {
                        studentRepository.findById(targetId)
                                .map(Student::getUser)
                                .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .ifPresentOrElse(tap_nguoi_nhan::add, () -> {
                                    studentRepository.findByUser_UserId(targetId)
                                            .map(Student::getUser)
                                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                            .ifPresent(tap_nguoi_nhan::add);
                                });
                    } else {
                        studentRepository.findAll().stream()
                                .map(Student::getUser)
                                .filter(Objects::nonNull)
                                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .forEach(tap_nguoi_nhan::add);
                    }
                }
                break;

            case "STAFF":
                if (staffRepository != null) {
                    if (targetId != null) {
                        staffRepository.findById(targetId)
                                .map(Staff::getUser)
                                .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .ifPresentOrElse(tap_nguoi_nhan::add, () -> {
                                    staffRepository.findByUser_UserId(targetId)
                                            .map(Staff::getUser)
                                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                            .ifPresent(tap_nguoi_nhan::add);
                                });
                    } else {
                        staffRepository.findAll().stream()
                                .map(Staff::getUser)
                                .filter(Objects::nonNull)
                                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .forEach(tap_nguoi_nhan::add);
                        if (userRepository != null) {
                            tap_nguoi_nhan.addAll(userRepository.findActiveStaffAndAdmins());
                        }
                    }
                }
                break;

            case "ROOM":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsWithStudentAndUserByRoomId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .forEach(tap_nguoi_nhan::add);
                }
                break;

            case "BUILDING":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByBuildingId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .forEach(tap_nguoi_nhan::add);
                }
                break;

            case "FLOOR":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByFloorId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .forEach(tap_nguoi_nhan::add);
                }
                break;

            default:
                log.warn("Bỏ qua phân phối: Loại đối tượng nhận thông báo không hợp lệ '{}'", targetType);
                break;
        }

        // Tối ưu gom tìm kiếm danh sách người nhận đã tồn tại trong 1 truy vấn thay vì
        // lặp N lần
        Set<String> existingUserIds = Collections.emptySet();
        if (notificationRecipientRepository != null) {
            existingUserIds = notificationRecipientRepository
                    .findByNotification_NotificationId(notification.getNotificationId()).stream()
                    .filter(nr -> nr.getUser() != null && nr.getUser().getUserId() != null)
                    .map(nr -> nr.getUser().getUserId())
                    .collect(Collectors.toSet());
        }

        List<NotificationRecipient> danh_sach_nguoi_nhan_moi = new ArrayList<>();
        for (User nguoi_nhan : tap_nguoi_nhan) {
            if (nguoi_nhan != null && nguoi_nhan.getUserId() != null
                    && !existingUserIds.contains(nguoi_nhan.getUserId())) {
                danh_sach_nguoi_nhan_moi.add(NotificationRecipient.builder()
                        .recipientId(java.util.UUID.randomUUID().toString())
                        .notification(notification)
                        .user(nguoi_nhan)
                        .isRead(false)
                        .readAt(null)
                        .build());
            }
        }

        if (!danh_sach_nguoi_nhan_moi.isEmpty() && notificationRecipientRepository != null) {
            notificationRecipientRepository.saveAll(danh_sach_nguoi_nhan_moi);
            log.info("Đã tạo {} bản ghi người nhận cho thông báo ID={}", danh_sach_nguoi_nhan_moi.size(),
                    notification.getNotificationId());
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
            if (actor != null && actor.getUserId() != null && userRepository != null) {
                managedActor = userRepository.findById(actor.getUserId()).orElse(null);
            }
            if (managedActor == null && userRepository != null) {
                managedActor = userRepository.findAll().stream()
                        .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleCode()))
                        .findFirst()
                        .orElse(null);
            }
            if (managedActor == null)
                return;

            Notification thong_bao = Notification.builder()
                    .notificationId(java.util.UUID.randomUUID().toString())
                    .title(title)
                    .content(content)
                    .targetType("STAFF")
                    .targetId(targetId)
                    .createdBy(managedActor)
                    .isPublished(true)
                    .publishedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            thong_bao = notificationRepository.saveAndFlush(thong_bao);
            createRecipientsForNotification(thong_bao);
            log.info("Đã tạo thông báo hệ thống cho Staff/Admin: {}", title);
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo hệ thống tới Staff/Admin: {}", e.getMessage());
        }
    }

    // Gửi thông báo hệ thống trực tiếp cho một sinh viên cụ thể
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStudent(Student student, String title, String content, User actor) {
        try {
            if (student == null || student.getUser() == null)
                return;
            String studentUserId = student.getUser().getUserId();
            if (studentUserId == null || userRepository == null)
                return;

            User nguoi_dung_sinh_vien = userRepository.findById(studentUserId).orElse(null);
            if (nguoi_dung_sinh_vien == null || !"ACTIVE".equalsIgnoreCase(nguoi_dung_sinh_vien.getStatus()))
                return;

            User managedActor = null;
            if (actor != null && actor.getUserId() != null) {
                managedActor = userRepository.findById(actor.getUserId()).orElse(null);
            }
            if (managedActor == null) {
                managedActor = userRepository.findAll().stream()
                        .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleCode()))
                        .findFirst()
                        .orElse(nguoi_dung_sinh_vien);
            }
            if (managedActor == null) {
                managedActor = nguoi_dung_sinh_vien;
            }

            Notification thong_bao = Notification.builder()
                    .notificationId(java.util.UUID.randomUUID().toString())
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
                    .recipientId(java.util.UUID.randomUUID().toString())
                    .notification(thong_bao)
                    .user(nguoi_dung_sinh_vien)
                    .isRead(false)
                    .readAt(null)
                    .build();

            notificationRecipientRepository.saveAndFlush(nguoi_nhan);
            log.info("Đã gửi thông báo tới sinh viên {}: {}", student.getFullName(), title);
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo tới sinh viên: {}", e.getMessage());
        }
    }
}
