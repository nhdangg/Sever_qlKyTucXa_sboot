package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface Payment_Repository extends JpaRepository<Payment, String> {
    Optional<Payment> findByPaymentCode(String paymentCode);
    boolean existsByPaymentCode(String paymentCode);
    List<Payment> findByInvoice_InvoiceId(String invoiceId);
    boolean existsByInvoice_InvoiceId(String invoiceId);
    boolean existsByInvoice_InvoiceIdAndStatus(String invoiceId, String status);
    List<Payment> findByStatus(String status);
    boolean existsByTransactionRef(String transactionRef);
    Optional<Payment> findByTransactionRef(String transactionRef);

    // Tìm kiếm giao dịch thanh toán theo mã hóa đơn, phương thức, trạng thái và khoảng thời gian
    @Query("""
            SELECT p FROM Payment p
            LEFT JOIN p.invoice i
            WHERE (:invoiceCode IS NULL OR :invoiceCode = '' OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :invoiceCode, '%')))
              AND (:paymentMethod IS NULL OR :paymentMethod = '' OR p.paymentMethod = :paymentMethod)
              AND (:status IS NULL OR :status = '' OR p.status = :status)
              AND (:fromDate IS NULL OR p.paymentDate >= :fromDate)
              AND (:toDate IS NULL OR p.paymentDate <= :toDate)
            """)
    Page<Payment> searchPayments(
            @Param("invoiceCode") String invoiceCode,
            @Param("paymentMethod") String paymentMethod,
            @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
