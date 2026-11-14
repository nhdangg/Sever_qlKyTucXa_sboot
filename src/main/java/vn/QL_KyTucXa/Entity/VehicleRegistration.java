package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dangKyXe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maDangKyXe", length = 36)
    private String vehicleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien", nullable = false)
    private Student student;

    @Column(name = "loaiXe", length = 20, nullable = false)
    private String vehicleType;

    @Column(name = "bienSoXe", length = 20)
    private String licensePlate;

    @Column(name = "dongXe", length = 100)
    private String brandModel;

    @Column(name = "phiHangThang", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    @Column(name = "ngayDangKy", nullable = false)
    private LocalDate registrationDate;

    @Column(name = "ngayHetHan")
    private LocalDate expiryDate;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "ngay_tao")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
