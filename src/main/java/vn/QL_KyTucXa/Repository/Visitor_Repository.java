package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Visitor;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface Visitor_Repository extends JpaRepository<Visitor, String> {
    List<Visitor> findByHostStudent_StudentId(String studentId);
    List<Visitor> findByRoom_RoomId(String roomId);

    // Đếm số lượng khách đang thực tế ở trong KTX mà sinh viên đón tiếp (loại trừ đơn chờ duyệt, hủy, từ chối)
    @Query("SELECT COUNT(v) FROM Visitor v WHERE v.hostStudent.studentId = :studentId AND v.checkOutTime IS NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%')")
    long countActiveVisitorsByStudentId(@Param("studentId") String studentId);

    // Đếm số lượng khách đang thực tế có mặt trong phòng
    @Query("SELECT COUNT(v) FROM Visitor v WHERE v.room.roomId = :roomId AND v.checkOutTime IS NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%')")
    long countActiveVisitorsByRoomId(@Param("roomId") String roomId);

    // Kiểm tra khách có cùng số CCCD đang ở trong KTX chưa check-out hay không
    @Query("SELECT COUNT(v) > 0 FROM Visitor v WHERE v.nationalId = :nationalId AND v.checkOutTime IS NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%')")
    boolean existsByNationalIdAndCheckOutTimeIsNull(@Param("nationalId") String nationalId);

    // Lấy danh sách khách thăm đang thực tế có mặt trong ký túc xá
    @Query("""
            SELECT v FROM Visitor v
            WHERE v.checkOutTime IS NULL
              AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%')
            """)
    List<Visitor> findActiveVisitorsInDorm();

    // Tìm kiếm khách thăm có phân trang theo từ khóa, sinh viên đón tiếp, phòng, trạng thái và khoảng thời gian
    @Query("""
            SELECT v FROM Visitor v
            LEFT JOIN v.hostStudent s
            LEFT JOIN v.room r
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR LOWER(v.visitorName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(v.nationalId, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:hostStudentId IS NULL OR :hostStudentId = '' OR s.studentId = :hostStudentId)
              AND (:roomId IS NULL OR :roomId = '' OR r.roomId = :roomId)
              AND (:status IS NULL OR :status = ''
                   OR (:status = 'PENDING' AND v.checkOutTime IS NULL AND v.purpose LIKE '[PENDING]%')
                   OR (:status = 'IN_DORM' AND v.checkOutTime IS NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%'))
                   OR (:status = 'CHECKED_OUT' AND v.checkOutTime IS NOT NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%'))
                   OR (:status = 'REJECTED' AND v.purpose LIKE '[REJECTED]%')
                   OR (:status = 'CANCELLED' AND v.purpose LIKE '[CANCELLED]%'))
              AND (:currentlyInDorm IS NULL
                   OR (:currentlyInDorm = true AND v.checkOutTime IS NULL AND (v.purpose IS NULL OR v.purpose NOT LIKE '[%]%'))
                   OR (:currentlyInDorm = false AND (v.checkOutTime IS NOT NULL OR v.purpose LIKE '[%]%')))
              AND (:fromDate IS NULL OR v.checkInTime >= :fromDate)
              AND (:toDate IS NULL OR v.checkInTime <= :toDate)
            """)
    Page<Visitor> searchVisitors(
            @Param("keyword") String keyword,
            @Param("hostStudentId") String hostStudentId,
            @Param("roomId") String roomId,
            @Param("status") String status,
            @Param("currentlyInDorm") Boolean currentlyInDorm,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
