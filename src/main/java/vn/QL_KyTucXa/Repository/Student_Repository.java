package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Student;

import java.util.List;
import java.util.Optional;

@Repository
public interface Student_Repository extends JpaRepository<Student, String> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    Optional<Student> findById(String id);

    @EntityGraph(attributePaths = {"user"})
    Optional<Student> findByStudentCode(String studentCode);

    @EntityGraph(attributePaths = {"user"})
    Optional<Student> findByUser_UserId(String userId);
    boolean existsByUser_UserId(String userId);

    Optional<Student> findByNationalId(String nationalId);
    boolean existsByNationalId(String nationalId);
    boolean existsByNationalIdAndStudentIdNot(String nationalId, String studentId);
    boolean existsByStudentCode(String studentCode);
    List<Student> findByStatus(String status);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT s FROM Student s
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.nationalId, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:faculty IS NULL OR :faculty = ''
                OR LOWER(s.faculty) = LOWER(:faculty)
                OR LOWER(s.faculty) LIKE LOWER(CONCAT('%', :faculty, '%')))
              AND (:className IS NULL OR :className = ''
                OR LOWER(s.className) = LOWER(:className)
                OR LOWER(s.className) LIKE LOWER(CONCAT('%', :className, '%')))
              AND (:gender IS NULL OR :gender = '' OR UPPER(s.gender) = UPPER(:gender))
              AND (:status IS NULL OR :status = '' OR UPPER(s.status) = UPPER(:status))
              AND (:isResident IS NULL
                OR (:isResident = true AND EXISTS (SELECT 1 FROM Contract c WHERE c.student = s AND c.status = 'ACTIVE'))
                OR (:isResident = false AND NOT EXISTS (SELECT 1 FROM Contract c WHERE c.student = s AND c.status = 'ACTIVE')))
            """)
    Page<Student> searchStudents(
            @Param("keyword") String keyword,
            @Param("faculty") String faculty,
            @Param("className") String className,
            @Param("gender") String gender,
            @Param("status") String status,
            @Param("isResident") Boolean isResident,
            Pageable pageable
    );

    default Page<Student> searchStudents(String keyword, String status, Pageable pageable) {
        return searchStudents(keyword, null, null, null, status, null, pageable);
    }
}
