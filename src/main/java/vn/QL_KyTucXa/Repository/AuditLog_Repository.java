package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.AuditLog;

import java.util.List;

@Repository
public interface AuditLog_Repository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByUser_UserId(String userId);
    List<AuditLog> findByTableName(String tableName);
    List<AuditLog> findByAction(String action);
}