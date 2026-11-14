package vn.QL_KyTucXa.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface Invoice_Repository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByInvoiceCode(String invoiceCode);
    boolean existsByInvoiceCode(String invoiceCode);
    boolean existsByContract_ContractId(String contractId);
    boolean existsByContract_ContractIdAndInvoiceMonth(String contractId, LocalDate invoiceMonth);
    List<Invoice> findByContract_ContractIdAndInvoiceMonth(String contractId, LocalDate invoiceMonth);
    List<Invoice> findByStudent_StudentId(String studentId);
    List<Invoice> findByContract_ContractId(String contractId);
    List<Invoice> findByStatus(String status);
    List<Invoice> findByInvoiceMonth(LocalDate invoiceMonth);

    // Kiểm tra đã có hóa đơn phát hành hoặc đã thanh toán cho phòng trong tháng hay chưa
    @Query("""
            SELECT COUNT(i) > 0 FROM Invoice i
            JOIN i.contract c
            JOIN c.bed b
            WHERE b.room.roomId = :roomId
              AND i.invoiceMonth = :invoiceMonth
              AND i.status IN ('ISSUED', 'PAID', 'PARTIALLY_PAID')
            """)
    boolean existsIssuedInvoiceForRoomAndMonth(@Param("roomId") String roomId, @Param("invoiceMonth") LocalDate invoiceMonth);

    // Kiểm tra sinh viên có hóa đơn chưa thanh toán hoặc đang nợ quá hạn
    @Query("""
            SELECT COUNT(i) > 0 FROM Invoice i
            WHERE i.student.studentId = :studentId
              AND i.status IN ('ISSUED', 'OVERDUE', 'PARTIALLY_PAID')
            """)
    boolean existsUnpaidInvoiceByStudentId(@Param("studentId") String studentId);

    // Khóa bi quan bản ghi hóa đơn để chống tranh chấp đồng thời khi thanh toán
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdForUpdate(@Param("invoiceId") String invoiceId);

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN i.student s
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR i.status = :status)
              AND (:invoiceMonth IS NULL OR i.invoiceMonth = :invoiceMonth)
            """)
    Page<Invoice> searchInvoices(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("invoiceMonth") LocalDate invoiceMonth,
            Pageable pageable
    );

    @Query("""
            SELECT i FROM Invoice i
            WHERE (i.status = 'ISSUED' OR (i.status = 'PARTIALLY_PAID' AND (i.lateFeeAmount IS NULL OR i.lateFeeAmount = 0)))
              AND i.dueDate < :currentDate
            """)
    List<Invoice> findOverdueInvoices(@Param("currentDate") LocalDate currentDate);
}
