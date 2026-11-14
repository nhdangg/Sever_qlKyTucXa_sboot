package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.RegistrationPeriod;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RegistrationPeriod_Repository extends JpaRepository<RegistrationPeriod, String> {
    List<RegistrationPeriod> findByStatus(String status);
    List<RegistrationPeriod> findByAcademicYear(String academicYear);
    List<RegistrationPeriod> findByAcademicYearAndStatus(String academicYear, String status);

    // Kiểm tra đợt đăng ký có thời gian trùng lặp trong cùng năm học (chỉ áp dụng đối với các đợt đang mở OPEN)
    @Query("""
            SELECT COUNT(p) > 0 FROM RegistrationPeriod p
            WHERE (:excludePeriodId IS NULL OR p.periodId != :excludePeriodId)
              AND p.status = 'OPEN'
              AND (:academicYear IS NULL OR :academicYear = '' OR p.academicYear = :academicYear)
              AND (p.startDate <= :endDate AND p.endDate >= :startDate)
            """)
    boolean hasOverlappingPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("academicYear") String academicYear,
            @Param("excludePeriodId") String excludePeriodId
    );

    // Tìm kiếm phân trang đợt đăng ký theo năm học và trạng thái
    @Query("""
            SELECT p FROM RegistrationPeriod p
            WHERE (:academicYear IS NULL OR :academicYear = '' OR p.academicYear = :academicYear)
              AND (:status IS NULL OR :status = '' OR p.status = :status)
            """)
    Page<RegistrationPeriod> searchPeriods(
            @Param("academicYear") String academicYear,
            @Param("status") String status,
            Pageable pageable
    );
}
