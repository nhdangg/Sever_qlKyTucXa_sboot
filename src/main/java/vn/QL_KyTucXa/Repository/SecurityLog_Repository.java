package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.SecurityLog;

import java.util.List;

@Repository
public interface SecurityLog_Repository extends JpaRepository<SecurityLog, String> {
    List<SecurityLog> findByEventType(String eventType);
    List<SecurityLog> findByStudent_StudentId(String studentId);
    List<SecurityLog> findByRoom_RoomId(String roomId);
}