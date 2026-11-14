package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Feedback;

import java.util.List;

@Repository
public interface Feedback_Repository extends JpaRepository<Feedback, String> {
    List<Feedback> findByStudent_StudentId(String studentId);
    List<Feedback> findByStatus(String status);

    @Query("""
            SELECT f FROM Feedback f
            LEFT JOIN f.student s
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(f.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(f.response, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR f.status = :status)
              AND (:category IS NULL OR :category = '' OR f.category = :category)
            """)
    Page<Feedback> searchFeedbacks(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category,
            Pageable pageable
    );
}
