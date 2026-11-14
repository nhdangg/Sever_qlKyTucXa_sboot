package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hoaDon", uniqueConstraints = @UniqueConstraint(
        name = "uq_contract_invoice_month",
        columnNames = {"hopDong", "thangHoaDon"}
    ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maHoaDon", length = 36)
    private String invoiceId;

    @Column(name = "maSoHoaDon", length = 30, nullable = false, unique = true)
    private String invoiceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hopDong", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien", nullable = false)
    private Student student;

    @Column(name = "thangHoaDon", nullable = false)
    private LocalDate invoiceMonth;

    @Column(name = "ngayPhatHanh", nullable = false)
    private LocalDate issueDate;

    @Column(name = "hanThanhToan", nullable = false)
    private LocalDate dueDate;

    @Column(name = "tongTienTruocGiam", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "tienMienGiam", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tienPhatTreHan", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "tongTienPhaiTra", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "tienDaThanhToan", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceItem> invoiceItems = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void calculateTotalAmount() {
        BigDecimal sub = this.subtotalAmount != null ? this.subtotalAmount : BigDecimal.ZERO;
        BigDecimal disc = this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO;
        BigDecimal late = this.lateFeeAmount != null ? this.lateFeeAmount : BigDecimal.ZERO;
        this.totalAmount = sub.subtract(disc).add(late);
    }
}
