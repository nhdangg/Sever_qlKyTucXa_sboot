package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.NotificationRecipient;

import java.util.Collection;
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

    @Modifying
    @Query(value = "DELETE FROM nguoi_nhan_thong_bao WHERE thong_bao = :notificationId", nativeQuery = true)
    int deleteRecipientsByNotificationId(@Param("notificationId") String notificationId);

    @Modifying
    @Query(value = "INSERT INTO nguoi_nhan_thong_bao (ma_nguoi_nhan, thong_bao, nguoi_dung, da_doc) " +
            "SELECT UUID(), :notificationId, u.ma_nguoi_dung, 0 " +
            "FROM nguoi_dung u " +
            "JOIN vai_tro r ON u.vai_tro = r.ma_vai_tro " +
            "WHERE u.trang_thai = 'ACTIVE' AND r.ma_code_vai_tro = 'STUDENT' " +
            "AND u.ma_nguoi_dung NOT IN (SELECT nr.nguoi_dung FROM nguoi_nhan_thong_bao nr WHERE nr.thong_bao = :notificationId)",
            nativeQuery = true)
    int insertRecipientsForStudents(@Param("notificationId") String notificationId);

    @Modifying
    @Query(value = "INSERT INTO nguoi_nhan_thong_bao (ma_nguoi_nhan, thong_bao, nguoi_dung, da_doc) " +
            "SELECT UUID(), :notificationId, u.ma_nguoi_dung, 0 " +
            "FROM nguoi_dung u " +
            "WHERE u.trang_thai = 'ACTIVE' " +
            "AND u.ma_nguoi_dung NOT IN (SELECT nr.nguoi_dung FROM nguoi_nhan_thong_bao nr WHERE nr.thong_bao = :notificationId)",
            nativeQuery = true)
    int insertRecipientsForAll(@Param("notificationId") String notificationId);

    @Modifying
    @Query(value = "INSERT INTO nguoi_nhan_thong_bao (ma_nguoi_nhan, thong_bao, nguoi_dung, da_doc) " +
            "SELECT UUID(), :notificationId, u.ma_nguoi_dung, 0 " +
            "FROM nguoi_dung u " +
            "JOIN vai_tro r ON u.vai_tro = r.ma_vai_tro " +
            "WHERE u.trang_thai = 'ACTIVE' AND r.ma_code_vai_tro IN ('ADMIN', 'STAFF', 'MANAGER') " +
            "AND u.ma_nguoi_dung NOT IN (SELECT nr.nguoi_dung FROM nguoi_nhan_thong_bao nr WHERE nr.thong_bao = :notificationId)",
            nativeQuery = true)
    int insertRecipientsForStaff(@Param("notificationId") String notificationId);

    @Modifying
    @Query(value = "INSERT INTO nguoi_nhan_thong_bao (ma_nguoi_nhan, thong_bao, nguoi_dung, da_doc) " +
            "SELECT UUID(), :notificationId, u.ma_nguoi_dung, 0 " +
            "FROM nguoi_dung u " +
            "WHERE u.ma_nguoi_dung IN (:userIds) " +
            "AND u.ma_nguoi_dung NOT IN (SELECT nr.nguoi_dung FROM nguoi_nhan_thong_bao nr WHERE nr.thong_bao = :notificationId)",
            nativeQuery = true)
    int insertRecipientsForUserIds(@Param("notificationId") String notificationId, @Param("userIds") Collection<String> userIds);
}
