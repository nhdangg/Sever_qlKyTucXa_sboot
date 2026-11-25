package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.AuditLog;

import java.util.List;

@Repository
public interface AuditLog_Repository extends JpaRepository<AuditLog, String> {
       List<AuditLog> findByUser_UserId(String userId);

       List<AuditLog> findByTableName(String tableName);

       List<AuditLog> findByAction(String action);

       // Tìm kiếm nhật ký kiểm toán với từ khóa hành động và tên bảng kèm phân trang
       @Query("SELECT a FROM AuditLog a LEFT JOIN a.user u WHERE " +
                     "(:keyword IS NULL OR :keyword = '' OR LOWER(a.action) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     " OR LOWER(a.tableName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     " OR LOWER(a.recordId) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     " OR (u IS NOT NULL AND LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                     " OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                     "AND (:action IS NULL OR :action = '' OR a.action = :action OR a.action LIKE CONCAT(:action, '_%')) "
                     +
                     "AND (:tableName IS NULL OR :tableName = '' OR a.tableName = :tableName)")
       Page<AuditLog> searchLogs(@Param("keyword") String keyword,
                     @Param("action") String action,
                     @Param("tableName") String tableName,
                     Pageable pageable);
}