package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Violation;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface Violation_Repository extends JpaRepository<Violation, String> {
    List<Violation> findByStudent_StudentId(String studentId);
    List<Violation> findByStatus(String status);
    List<Violation> findByViolationType_ViolationTypeId(String violationTypeId);
    boolean existsByViolationType_ViolationTypeId(String violationTypeId);

    // Tính tổng điểm trừ vi phạm của sinh viên (loại trừ các biên bản đã bị hủy)
    @Query("""
            SELECT COALESCE(SUM(v.penaltyPointsApplied), 0)
            FROM Violation v
            WHERE v.student.studentId = :studentId
              AND v.status <> 'CANCELLED'
            """)
    int sumPenaltyPointsByStudent(@Param("studentId") String studentId);

    // Lấy các biên bản vi phạm có phạt tiền chưa tính vào hóa đơn của sinh viên
    @Query("""
            SELECT v FROM Violation v
            WHERE v.student.studentId = :studentId
              AND v.status = 'RECORDED'
              AND v.fineAmount > 0
              AND v.violationDate <= :cutoffDate
            """)
    List<Violation> findUnbilledFinesByStudent(
            @Param("studentId") String studentId,
            @Param("cutoffDate") LocalDate cutoffDate
    );

    // Tìm kiếm biên bản vi phạm có phân trang với nhiều tiêu chí
    @Query("""
            SELECT v FROM Violation v
            LEFT JOIN v.student s
            LEFT JOIN v.violationType vt
            WHERE (:studentId IS NULL OR :studentId = '' OR s.studentId = :studentId)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:violationTypeId IS NULL OR :violationTypeId = '' OR vt.violationTypeId = :violationTypeId)
              AND (:severityLevel IS NULL OR :severityLevel = '' OR UPPER(vt.severityLevel) = UPPER(:severityLevel))
              AND (:status IS NULL OR :status = '' OR UPPER(v.status) = UPPER(:status))
              AND (:fromDate IS NULL OR v.violationDate >= :fromDate)
              AND (:toDate IS NULL OR v.violationDate <= :toDate)
            """)
    Page<Violation> searchViolations(
            @Param("studentId") String studentId,
            @Param("keyword") String keyword,
            @Param("violationTypeId") String violationTypeId,
            @Param("severityLevel") String severityLevel,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
