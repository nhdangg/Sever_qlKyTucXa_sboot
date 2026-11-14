package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "thanhToan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maThanhToan", length = 36)
    private String paymentId;

    @Column(name = "maSoThanhToan", length = 30, nullable = false, unique = true)
    private String paymentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hoaDon", nullable = false)
    private Invoice invoice;

    @Column(name = "phuongThucThanhToan", length = 20, nullable = false)
    private String paymentMethod;

    @Column(name = "soTien", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "maGiaoDichThamChieu", length = 100)
    private String transactionRef;

    @CreationTimestamp
    @Column(name = "ngayThanhToan", nullable = false, updatable = false)
    private LocalDateTime paymentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiNhanTien")
    private Staff receivedBy;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "ghiChu", length = 255)
    private String note;
}
