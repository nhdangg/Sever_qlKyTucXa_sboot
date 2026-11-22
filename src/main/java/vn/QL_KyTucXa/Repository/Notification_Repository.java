package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Notification;

import java.util.List;

@Repository
public interface Notification_Repository extends JpaRepository<Notification, String> {
    List<Notification> findByIsPublished(Boolean isPublished);
    Page<Notification> findByIsPublished(Boolean isPublished, Pageable pageable);
    List<Notification> findByIsPublishedAndTargetType(Boolean isPublished, String targetType);
    Page<Notification> findByIsPublishedAndTargetType(Boolean isPublished, String targetType, Pageable pageable);
    List<Notification> findByTargetType(String targetType);
    List<Notification> findByCreatedBy_UserId(String userId);

    // Tìm kiếm thông báo theo tiêu đề và trạng thái phát hành
    @Query("""
            SELECT n FROM Notification n
            WHERE (:title IS NULL OR :title = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :title, '%')))
              AND (:isPublished IS NULL OR n.isPublished = :isPublished)
            """)
    Page<Notification> searchNotifications(
            @Param("title") String title,
            @Param("isPublished") Boolean isPublished,
            Pageable pageable
    );
}
