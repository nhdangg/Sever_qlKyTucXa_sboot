package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Staff;

import java.util.List;
import java.util.Optional;

@Repository
public interface Staff_Repository extends JpaRepository<Staff, String> {
    Optional<Staff> findByStaffCode(String staffCode);
    Optional<Staff> findByUser_UserId(String userId);
    boolean existsByStaffCode(String staffCode);
    boolean existsByUser_UserId(String userId);
    boolean existsByUser_UserIdAndStaffIdNot(String userId, String staffId);
    List<Staff> findByStatus(String status);

    @Query("""
            SELECT s FROM Staff s
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:department IS NULL OR :department = ''
                OR LOWER(s.department) = LOWER(:department)
                OR LOWER(s.department) LIKE LOWER(CONCAT('%', :department, '%')))
              AND (:status IS NULL OR :status = '' OR UPPER(s.status) = UPPER(:status))
            """)
    Page<Staff> searchStaffs(
            @Param("keyword") String keyword,
            @Param("department") String department,
            @Param("status") String status,
            Pageable pageable
    );
}