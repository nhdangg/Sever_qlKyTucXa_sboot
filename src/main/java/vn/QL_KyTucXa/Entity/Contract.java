package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hopDong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maHopDong", length = 36)
    private String contractId;

    @Column(name = "maSoHopDong", length = 30, nullable = false, unique = true)
    private String contractCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "giuong", nullable = false)
    private Bed bed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dotDangKy", nullable = false)
    private RegistrationPeriod period;

    @Column(name = "ngayBatDau", nullable = false)
    private LocalDate startDate;

    @Column(name = "ngayKetThuc", nullable = false)
    private LocalDate endDate;

    @Column(name = "tienDatCoc", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "tienThueHangThang", precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlyFee;

    @Column(name = "ngayKy")
    private LocalDate signedDate;

    @Column(name = "ngayThanhLy")
    private LocalDate terminatedDate;

    @Column(name = "lyDoThanhLy", length = 255)
    private String terminationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDuyet")
    private Staff approver;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void syncTerminatedDate() {
        if ("TERMINATED".equalsIgnoreCase(this.status) || "EXPIRED".equalsIgnoreCase(this.status)) {
            if (this.terminatedDate == null) {
                this.terminatedDate = LocalDate.now();
            }
        } else if ("ACTIVE".equalsIgnoreCase(this.status)) {
            this.terminatedDate = null;
        }
    }
}
