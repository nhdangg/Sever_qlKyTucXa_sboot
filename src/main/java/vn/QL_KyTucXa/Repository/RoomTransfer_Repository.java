package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.RoomTransfer;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomTransfer_Repository extends JpaRepository<RoomTransfer, String> {
    List<RoomTransfer> findByContract_ContractId(String contractId);
    boolean existsByContract_ContractId(String contractId);

    List<RoomTransfer> findByStatus(String status);

    boolean existsByContract_ContractIdAndStatus(String contractId, String status);

    List<RoomTransfer> findByContract_Student_StudentId(String studentId);

    Page<RoomTransfer> findByContract_Student_StudentId(String studentId, Pageable pageable);

    // Tìm kiếm và lọc yêu cầu chuyển phòng theo trạng thái, sinh viên và khoảng ngày chuyển
    @Query("""
            SELECT rt FROM RoomTransfer rt
            LEFT JOIN rt.contract c
            LEFT JOIN c.student s
            WHERE (:status IS NULL OR :status = '' OR rt.status = :status)
              AND (:studentKeyword IS NULL OR :studentKeyword = ''
                   OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :studentKeyword, '%'))
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :studentKeyword, '%'))
                   OR s.studentId = :studentKeyword)
              AND (:fromDate IS NULL OR rt.transferDate >= :fromDate)
              AND (:toDate IS NULL OR rt.transferDate <= :toDate)
            """)
    Page<RoomTransfer> searchRoomTransfers(
            @Param("status") String status,
            @Param("studentKeyword") String studentKeyword,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
