package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.NotificationRecipient;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipient_Repository extends JpaRepository<NotificationRecipient, String> {
    List<NotificationRecipient> findByUser_UserId(String userId);
    List<NotificationRecipient> findByUser_UserIdAndIsRead(String userId, Boolean isRead);
    Page<NotificationRecipient> findByUser_UserId(String userId, Pageable pageable);
    Page<NotificationRecipient> findByUser_UserIdAndIsRead(String userId, Boolean isRead, Pageable pageable);
    long countByUser_UserIdAndIsRead(String userId, Boolean isRead);
    Optional<NotificationRecipient> findByNotification_NotificationIdAndUser_UserId(String notificationId, String userId);
    List<NotificationRecipient> findByNotification_NotificationId(String notificationId);
    void deleteByNotification_NotificationId(String notificationId);
}
