package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.QL_KyTucXa.DTO.Request.Notification_Request;
import vn.QL_KyTucXa.DTO.Response.Notification_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Notification_Mapper;
import vn.QL_KyTucXa.Repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Notification_ServiceTest {

    @Mock
    private Notification_Repository notificationRepository;
    @Mock
    private NotificationRecipient_Repository notificationRecipientRepository;
    @Mock
    private User_Repository userRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private Notification_Mapper notificationMapper;

    @InjectMocks
    private Notification_Service notificationService;

    private User adminUser;
    private User studentUser;
    private Notification notification;
    private Notification_Request request;
    private Notification_Response response;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().userId("user-admin").username("admin").build();
        studentUser = User.builder().userId("user-student").username("student01").build();

        notification = Notification.builder()
                .notificationId("notif-01")
                .title("Thông báo bảo trì điện nước")
                .content("Cắt điện từ 8h đến 12h")
                .targetType("ALL")
                .createdBy(adminUser)
                .isPublished(false)
                .createdAt(LocalDateTime.now())
                .build();

        request = Notification_Request.builder()
                .title("Thông báo bảo trì điện nước")
                .content("Cắt điện từ 8h đến 12h")
                .targetType("ALL")
                .isPublished(false)
                .build();

        response = Notification_Response.builder()
                .notificationId("notif-01")
                .title("Thông báo bảo trì điện nước")
                .content("Cắt điện từ 8h đến 12h")
                .targetType("ALL")
                .isPublished(false)
                .build();
    }

    @Test
    @DisplayName("Tạo thông báo chưa phát hành thành công và lấy người tạo từ JWT")
    void create_success_fromJwt_unpublished() {
        when(currentUserService.userId()).thenReturn("user-admin");
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        Notification_Response result = notificationService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Thông báo bảo trì điện nước");
        verify(notificationRepository).save(notification);
        verify(notificationRecipientRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Tạo thông báo phát hành ngay lập tức tạo danh sách người nhận")
    void create_success_publishedTrue_createsRecipients() {
        request.setIsPublished(true);
        notification.setIsPublished(true);
        notification.setPublishedAt(LocalDateTime.now());

        when(currentUserService.userId()).thenReturn("user-admin");
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);
        when(userRepository.findByStatus("ACTIVE")).thenReturn(List.of(adminUser, studentUser));
        when(notificationRecipientRepository.findByNotification_NotificationId("notif-01"))
                .thenReturn(List.of());

        Notification_Response result = notificationService.create(request);

        assertThat(result).isNotNull();
        verify(notificationRecipientRepository).saveAll(any());
    }

    @Test
    @DisplayName("Tạo thông báo thất bại khi không có thông tin xác thực")
    void create_unauthenticated_throwsException() {
        when(currentUserService.userId()).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        AppException ex = assertThrows(AppException.class, () -> notificationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Tạo thông báo thất bại khi targetType không hợp lệ (sai chính tả như BULDING)")
    void create_invalidTargetType_throwsException() {
        request.setTargetType("BULDING");
        notification.setTargetType("BULDING");

        when(currentUserService.userId()).thenReturn("user-admin");
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(notificationMapper.toEntity(request)).thenReturn(notification);

        AppException ex = assertThrows(AppException.class, () -> notificationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Loại đối tượng nhận thông báo 'BULDING' không hợp lệ");
    }

    @Test
    @DisplayName("Tạo thông báo thất bại khi targetType là ROOM/BUILDING/FLOOR/USER nhưng thiếu targetId")
    void create_missingTargetId_throwsException() {
        request.setTargetType("BUILDING");
        request.setTargetId(null);
        notification.setTargetType("BUILDING");
        notification.setTargetId(null);

        when(currentUserService.userId()).thenReturn("user-admin");
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(notificationMapper.toEntity(request)).thenReturn(notification);

        AppException ex = assertThrows(AppException.class, () -> notificationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(ex.getMessage()).contains("bắt buộc phải cung cấp targetId");
    }

    @Test
    @DisplayName("Tạo thông báo cho một phòng cụ thể gửi tới các sinh viên có hợp đồng ACTIVE")
    void create_targetTypeRoom_createsRecipientsForRoomOccupants() {
        request.setTargetType("ROOM");
        request.setTargetId("room-01");
        request.setIsPublished(true);

        notification.setTargetType("ROOM");
        notification.setTargetId("room-01");
        notification.setIsPublished(true);

        Room room = Room.builder().roomId("room-01").build();
        Bed bed = Bed.builder().bedId("bed-01").room(room).build();
        Student student = Student.builder().studentId("stu-01").user(studentUser).build();
        Contract contract = Contract.builder().contractId("c-01").bed(bed).student(student).status("ACTIVE").build();

        when(currentUserService.userId()).thenReturn("user-admin");
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);
        when(contractRepository.findActiveContractsWithStudentAndUserByRoomId("room-01")).thenReturn(List.of(contract));
        when(notificationRecipientRepository.findByNotification_NotificationId("notif-01"))
                .thenReturn(List.of());

        Notification_Response result = notificationService.create(request);

        assertThat(result).isNotNull();
        verify(notificationRecipientRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
    }

    @Test
    @DisplayName("Phát hành thông báo thành công và tạo người nhận")
    void publish_success() {
        notification.setIsPublished(false);
        when(notificationRepository.findById("notif-01")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);
        when(userRepository.findByStatus("ACTIVE")).thenReturn(List.of(adminUser, studentUser));
        when(notificationRecipientRepository.findByNotification_NotificationId("notif-01"))
                .thenReturn(List.of());

        Notification_Response result = notificationService.publish("notif-01");

        assertThat(result).isNotNull();
        assertThat(notification.getIsPublished()).isTrue();
        assertThat(notification.getPublishedAt()).isNotNull();
        verify(notificationRecipientRepository).saveAll(any());
    }

    @Test
    @DisplayName("Phát hành thông báo thất bại khi thông báo đã phát hành trước đó")
    void publish_alreadyPublished_throwsException() {
        notification.setIsPublished(true);
        when(notificationRepository.findById("notif-01")).thenReturn(Optional.of(notification));

        AppException ex = assertThrows(AppException.class, () -> notificationService.publish("notif-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Cập nhật thông báo chưa phát hành thành công")
    void update_success() {
        notification.setIsPublished(false);
        when(notificationRepository.findById("notif-01")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        Notification_Request updateReq = Notification_Request.builder()
                .title("Tiêu đề mới")
                .content("Nội dung mới")
                .build();

        Notification_Response result = notificationService.update("notif-01", updateReq);

        assertThat(result).isNotNull();
        assertThat(notification.getTitle()).isEqualTo("Tiêu đề mới");
        assertThat(notification.getContent()).isEqualTo("Nội dung mới");
    }

    @Test
    @DisplayName("Cập nhật thông báo thất bại khi thông báo đã phát hành")
    void update_alreadyPublished_throwsException() {
        notification.setIsPublished(true);
        when(notificationRepository.findById("notif-01")).thenReturn(Optional.of(notification));

        Notification_Request updateReq = Notification_Request.builder().title("Tiêu đề mới").build();

        AppException ex = assertThrows(AppException.class, () -> notificationService.update("notif-01", updateReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa thông báo thành công và xóa sạch người nhận liên quan trước đó")
    void delete_success_deletesRecipientsFirst() {
        NotificationRecipient recipient = NotificationRecipient.builder().recipientId("rec-01").build();
        when(notificationRepository.findById("notif-01")).thenReturn(Optional.of(notification));
        when(notificationRecipientRepository.findByNotification_NotificationId("notif-01"))
                .thenReturn(List.of(recipient));

        notificationService.delete("notif-01");

        verify(notificationRecipientRepository).deleteAll(List.of(recipient));
        verify(notificationRepository).delete(notification);
    }

    @Test
    @DisplayName("Lấy danh sách thông báo đã phát hành có phân trang")
    void getPublishedNotifications_paginated_success() {
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByIsPublishedAndTargetType(eq(true), eq("ALL"), any(Pageable.class))).thenReturn(page);
        when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

        Page_Response<Notification_Response> result = notificationService.getPublishedNotifications(1, 10, "publishedAt", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm thông báo theo tiêu đề và trạng thái phát hành")
    void getAll_withSearchCriteria() {
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.searchNotifications(eq("bảo trì"), eq(true), any(Pageable.class))).thenReturn(page);
        when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

        Page_Response<Notification_Response> result = notificationService.getAll(1, 10, "bảo trì", true, "createdAt", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }
}

