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
import vn.QL_KyTucXa.DTO.Response.NotificationRecipient_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Notification;
import vn.QL_KyTucXa.Entity.NotificationRecipient;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.NotificationRecipient_Mapper;
import vn.QL_KyTucXa.Repository.NotificationRecipient_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRecipient_ServiceTest {

    @Mock
    private NotificationRecipient_Repository notificationRecipientRepository;
    @Mock
    private NotificationRecipient_Mapper recipientMapper;
    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private NotificationRecipient_Service recipientService;

    private User user;
    private Notification notification;
    private NotificationRecipient recipient;
    private NotificationRecipient_Response response;

    @BeforeEach
    void setUp() {
        user = User.builder().userId("user-01").username("student01").build();
        notification = Notification.builder().notificationId("notif-01").title("Tiêu đề").build();

        recipient = NotificationRecipient.builder()
                .recipientId("rec-01")
                .notification(notification)
                .user(user)
                .isRead(false)
                .build();

        response = NotificationRecipient_Response.builder()
                .recipientId("rec-01")
                .notificationId("notif-01")
                .notificationTitle("Tiêu đề")
                .userId("user-01")
                .isRead(false)
                .build();
    }

    @Test
    @DisplayName("Lấy danh sách thông báo trong hộp thư của cá nhân")
    void getMyNotifications_success() {
        Page<NotificationRecipient> page = new PageImpl<>(List.of(recipient));
        when(currentUserService.userId()).thenReturn("user-01");
        when(notificationRecipientRepository.findByUser_UserId(eq("user-01"), any(Pageable.class))).thenReturn(page);
        when(recipientMapper.toResponseList(List.of(recipient))).thenReturn(List.of(response));

        Page_Response<NotificationRecipient_Response> result = recipientService.getMyNotifications(1, 10, null);

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Đánh dấu thông báo là đã đọc thành công")
    void markAsRead_success() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(notificationRecipientRepository.findById("rec-01")).thenReturn(Optional.of(recipient));
        when(notificationRecipientRepository.save(any(NotificationRecipient.class))).thenReturn(recipient);
        when(recipientMapper.toResponse(recipient)).thenReturn(response);

        NotificationRecipient_Response result = recipientService.markAsRead("rec-01");

        assertThat(result).isNotNull();
        assertThat(recipient.getIsRead()).isTrue();
        assertThat(recipient.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("Đánh dấu thông báo đã đọc thất bại khi không phải chủ sở hữu")
    void markAsRead_failed_notOwner() {
        when(currentUserService.userId()).thenReturn("user-different");
        when(notificationRecipientRepository.findById("rec-01")).thenReturn(Optional.of(recipient));

        AppException ex = assertThrows(AppException.class, () -> recipientService.markAsRead("rec-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Đánh dấu tất cả thông báo là đã đọc")
    void markAllAsRead_success() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(notificationRecipientRepository.findByUser_UserIdAndIsRead("user-01", false))
                .thenReturn(List.of(recipient));

        recipientService.markAllAsRead();

        assertThat(recipient.getIsRead()).isTrue();
        assertThat(recipient.getReadAt()).isNotNull();
        verify(notificationRecipientRepository).saveAll(List.of(recipient));
    }

    @Test
    @DisplayName("Đếm số lượng thông báo chưa đọc")
    void getUnreadCount_success() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(notificationRecipientRepository.countByUser_UserIdAndIsRead("user-01", false)).thenReturn(5L);

        long count = recipientService.getUnreadCount();

        assertThat(count).isEqualTo(5L);
    }
}
