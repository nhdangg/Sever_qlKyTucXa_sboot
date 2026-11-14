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
            "notificationId", "title", "targetType", "isPublished", "publishedAt", "createdAt"
    );

    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of(
            "ALL", "USER", "STUDENT", "STAFF", "ROOM", "FLOOR", "BUILDING"
    );

    private static final Set<String> REQUIRE_TARGET_ID_TYPES = Set.of(
            "USER", "ROOM", "FLOOR", "BUILDING"
    );

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

        User user = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Notification notification = notificationMapper.toEntity(request);
        notification.setCreatedBy(user);
        String targetType = (notification.getTargetType() == null || notification.getTargetType().isBlank())
                ? "ALL"
                : notification.getTargetType().trim().toUpperCase();
        String targetId = (request.getTargetId() != null && !request.getTargetId().isBlank())
                ? request.getTargetId().trim()
                : null;

        validateTarget(targetType, targetId);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);

        boolean isPub = Boolean.TRUE.equals(request.getIsPublished());
        notification.setIsPublished(isPub);
        if (isPub) {
            notification.setPublishedAt(LocalDateTime.now());
        } else {
            notification.setPublishedAt(null);
        }

        notification = notificationRepository.save(notification);

        // Tạo người nhận thông báo nếu đã phát hành ngay lúc tạo
        if (isPub) {
            createRecipientsForNotification(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    // Lấy thông tin chi tiết thông báo theo ID
    @Transactional(readOnly = true)
    public Notification_Response getById(String notificationId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên chỉ được xem thông báo qua Hộp thư (/notifications/inbox)");
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        return notificationMapper.toResponse(notification);
    }

    // Lấy danh sách thông báo đã phát hành đại chúng (targetType = ALL) có phân trang
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getPublishedNotifications(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "publishedAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Notification> pageResult = notificationRepository.findByIsPublishedAndTargetType(true, "ALL", pageable);
        List<Notification_Response> data = notificationMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Notification_Response>builder()
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

    // Lấy danh sách thông báo đã phát hành đại chúng (targetType = ALL) không phân trang
    @Transactional(readOnly = true)
    public List<Notification_Response> getPublishedNotifications() {
        List<Notification> list = notificationRepository.findByIsPublishedAndTargetType(true, "ALL");
        return notificationMapper.toResponseList(list);
    }

    // Lấy danh sách tất cả thông báo phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và phân trang danh sách thông báo theo tiêu đề và trạng thái phát hành
    @Transactional(readOnly = true)
    public Page_Response<Notification_Response> getAll(
            int page,
            int size,
            String title,
            Boolean isPublished,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên chỉ được xem thông báo qua Hộp thư (/notifications/inbox)");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String titleFilter = (title != null && !title.isBlank()) ? title.trim() : null;

        Page<Notification> pageResult = notificationRepository.searchNotifications(titleFilter, isPublished, pageable);
        List<Notification_Response> data = notificationMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Notification_Response>builder()
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

    // Cập nhật thông báo chỉ cho phép khi thông báo chưa được phát hành
    @Transactional
    public Notification_Response update(String notificationId, Notification_Request request) {
        log.info("Cập nhật thông báo có ID: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (Boolean.TRUE.equals(notification.getIsPublished())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể cập nhật thông báo đã phát hành");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            notification.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            notification.setContent(request.getContent().trim());
        }

        String targetType = notification.getTargetType();
        if (request.getTargetType() != null && !request.getTargetType().isBlank()) {
            targetType = request.getTargetType().trim().toUpperCase();
        }
        String targetId = notification.getTargetId();
        if (request.getTargetId() != null) {
            targetId = request.getTargetId().isBlank() ? null : request.getTargetId().trim();
        }

        validateTarget(targetType, targetId);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);

        boolean isPub = Boolean.TRUE.equals(request.getIsPublished());
        if (isPub) {
            notification.setIsPublished(true);
            notification.setPublishedAt(LocalDateTime.now());
        }

        notification = notificationRepository.save(notification);

        if (isPub) {
            createRecipientsForNotification(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    // Phát hành thông báo và phân phối đến hộp thư người nhận
    @Transactional
    public Notification_Response publish(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (Boolean.TRUE.equals(notification.getIsPublished())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Thông báo này đã được phát hành trước đó");
        }

        notification.setIsPublished(true);
        notification.setPublishedAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);

        createRecipientsForNotification(notification);

        return notificationMapper.toResponse(notification);
    }

    // Xóa thông báo và xóa tất cả người nhận liên quan trước để tránh lỗi khóa ngoại
    @Transactional
    public void delete(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        List<NotificationRecipient> recipients = notificationRecipientRepository.findByNotification_NotificationId(notificationId);
        if (recipients != null && !recipients.isEmpty()) {
            notificationRecipientRepository.deleteAll(recipients);
            log.info("Đã xóa {} người nhận trước khi xóa thông báo {}", recipients.size(), notificationId);
        }

        notificationRepository.delete(notification);
    }

    // Tạo danh sách người nhận thông báo theo loại đối tượng targetType và targetId
    private void createRecipientsForNotification(Notification notification) {
        if (notification == null || notification.getNotificationId() == null) {
            return;
        }

        Set<User> recipients = new HashSet<>();
        String targetType = notification.getTargetType() != null ? notification.getTargetType().trim().toUpperCase() : "ALL";
        String targetId = notification.getTargetId() != null ? notification.getTargetId().trim() : null;

        switch (targetType) {
            case "ALL":
                if (userRepository != null) {
                    recipients.addAll(userRepository.findByStatus("ACTIVE"));
                }
                break;

            case "USER":
                if (targetId != null && userRepository != null) {
                    userRepository.findById(targetId)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .ifPresent(recipients::add);
                }
                break;

            case "STUDENT":
                if (studentRepository != null) {
                    if (targetId != null) {
                        studentRepository.findById(targetId)
                                .map(Student::getUser)
                                .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .ifPresentOrElse(recipients::add, () -> {
                                    studentRepository.findByUser_UserId(targetId)
                                            .map(Student::getUser)
                                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                            .ifPresent(recipients::add);
                                });
                    } else {
                        studentRepository.findAll().stream()
                                .map(Student::getUser)
                                .filter(Objects::nonNull)
                                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .forEach(recipients::add);
                    }
                }
                break;

            case "STAFF":
                if (staffRepository != null) {
                    if (targetId != null) {
                        staffRepository.findById(targetId)
                                .map(Staff::getUser)
                                .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .ifPresentOrElse(recipients::add, () -> {
                                    staffRepository.findByUser_UserId(targetId)
                                            .map(Staff::getUser)
                                            .filter(u -> u != null && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                            .ifPresent(recipients::add);
                                });
                    } else {
                        staffRepository.findAll().stream()
                                .map(Staff::getUser)
                                .filter(Objects::nonNull)
                                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                                .forEach(recipients::add);
                        if (userRepository != null) {
                            recipients.addAll(userRepository.findActiveStaffAndAdmins());
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
                            .forEach(recipients::add);
                }
                break;

            case "BUILDING":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByBuildingId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .forEach(recipients::add);
                }
                break;

            case "FLOOR":
                if (targetId != null && contractRepository != null) {
                    contractRepository.findActiveContractsByFloorId(targetId).stream()
                            .map(c -> c.getStudent() != null ? c.getStudent().getUser() : null)
                            .filter(Objects::nonNull)
                            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                            .forEach(recipients::add);
                }
                break;

            default:
                log.warn("Bỏ qua phân phối: Loại đối tượng nhận thông báo không hợp lệ '{}'", targetType);
                break;
        }

        // Tối ưu gom tìm kiếm danh sách người nhận đã tồn tại trong 1 truy vấn thay vì lặp N lần
        Set<String> existingUserIds = Collections.emptySet();
        if (notificationRecipientRepository != null) {
            existingUserIds = notificationRecipientRepository.findByNotification_NotificationId(notification.getNotificationId()).stream()
                    .filter(nr -> nr.getUser() != null && nr.getUser().getUserId() != null)
                    .map(nr -> nr.getUser().getUserId())
                    .collect(Collectors.toSet());
        }

        List<NotificationRecipient> recipientEntities = new ArrayList<>();
        for (User u : recipients) {
            if (u != null && u.getUserId() != null && !existingUserIds.contains(u.getUserId())) {
                recipientEntities.add(NotificationRecipient.builder()
                        .notification(notification)
                        .user(u)
                        .isRead(false)
                        .readAt(null)
                        .build());
            }
        }

        if (!recipientEntities.isEmpty() && notificationRecipientRepository != null) {
            notificationRecipientRepository.saveAll(recipientEntities);
            log.info("Đã tạo {} bản ghi người nhận cho thông báo ID={}", recipientEntities.size(), notification.getNotificationId());
        }
    }

    private void validateTarget(String targetType, String targetId) {
        if (targetType == null || !ALLOWED_TARGET_TYPES.contains(targetType)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Loại đối tượng nhận thông báo '" + targetType + "' không hợp lệ. Cho phép: " + ALLOWED_TARGET_TYPES);
        }
        if (REQUIRE_TARGET_ID_TYPES.contains(targetType) && (targetId == null || targetId.isBlank())) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "Mục tiêu nhận thông báo '" + targetType + "' bắt buộc phải cung cấp targetId");
        }
    }

    // Gửi thông báo hệ thống tự động tới toàn bộ Ban Quản lý và Cán bộ / Nhân viên KTX
    @Transactional
    public void notifyStaffAndAdmins(String title, String content, User actor) {
        notifyStaffAndAdmins(title, content, actor, null);
    }

    @Transactional
    public void notifyStaffAndAdmins(String title, String content, User actor, String targetId) {
        try {
            if (actor == null && userRepository != null) {
                actor = userRepository.findAll().stream()
                        .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleCode()))
                        .findFirst()
                        .orElse(null);
            }
            if (actor == null) return;

            Notification notification = Notification.builder()
                    .title(title)
                    .content(content)
                    .targetType("STAFF")
                    .targetId(targetId)
                    .createdBy(actor)
                    .isPublished(true)
                    .publishedAt(LocalDateTime.now())
                    .build();

            notification = notificationRepository.save(notification);
            createRecipientsForNotification(notification);
            log.info("Đã tạo thông báo hệ thống cho Staff/Admin: {}", title);
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo hệ thống tới Staff/Admin: {}", e.getMessage());
        }
    }

    // Gửi thông báo hệ thống trực tiếp cho một sinh viên cụ thể
    @Transactional
    public void notifyStudent(Student student, String title, String content, User actor) {
        try {
            if (student == null || student.getUser() == null) return;
            User studentUser = student.getUser();
            if (!"ACTIVE".equalsIgnoreCase(studentUser.getStatus())) return;

            if (actor == null && userRepository != null) {
                actor = userRepository.findAll().stream()
                        .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleCode()))
                        .findFirst()
                        .orElse(studentUser);
            }
            if (actor == null) {
                actor = studentUser;
            }

            Notification notification = Notification.builder()
                    .title(title)
                    .content(content)
                    .targetType("USER")
                    .targetId(studentUser.getUserId())
                    .createdBy(actor)
                    .isPublished(true)
                    .publishedAt(LocalDateTime.now())
                    .build();

            notification = notificationRepository.save(notification);

            NotificationRecipient recipient = NotificationRecipient.builder()
                    .notification(notification)
                    .user(studentUser)
                    .isRead(false)
                    .readAt(null)
                    .build();

            notificationRecipientRepository.save(recipient);
            log.info("Đã gửi thông báo tới sinh viên {}: {}", student.getFullName(), title);
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo tới sinh viên: {}", e.getMessage());
        }
    }
}
