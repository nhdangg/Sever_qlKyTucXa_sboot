package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.MaintenanceRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRequest_Repository extends JpaRepository<MaintenanceRequest, String> {
    Optional<MaintenanceRequest> findByRequestCode(String requestCode);
    boolean existsByRequestCode(String requestCode);
    List<MaintenanceRequest> findByRoom_RoomId(String roomId);
    boolean existsByRoom_RoomId(String roomId);
    List<MaintenanceRequest> findByReportedBy_UserId(String userId);
    List<MaintenanceRequest> findByStatus(String status);
    List<MaintenanceRequest> findByPriority(String priority);

    // Tìm kiếm yêu cầu bảo trì theo từ khóa trạng thái mức ưu tiên số phòng danh mục
    @Query("""
            SELECT m FROM MaintenanceRequest m
            LEFT JOIN m.room r
            LEFT JOIN m.reportedBy u
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(m.requestCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(r.roomNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR m.status = :status)
              AND (:priority IS NULL OR :priority = '' OR m.priority = :priority)
              AND (:roomNumber IS NULL OR :roomNumber = '' OR LOWER(COALESCE(r.roomNumber, '')) LIKE LOWER(CONCAT('%', :roomNumber, '%')))
              AND (:category IS NULL OR :category = '' OR m.category = :category)
            """)
    Page<MaintenanceRequest> searchMaintenanceRequests(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("roomNumber") String roomNumber,
            @Param("category") String category,
            Pageable pageable
    );
}
