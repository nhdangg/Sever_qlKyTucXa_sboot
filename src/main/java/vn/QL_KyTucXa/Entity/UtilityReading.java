package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chiSoDienNuoc", uniqueConstraints = {
    @UniqueConstraint(name = "uq_room_service_month", columnNames = {"phong", "loaiDichVu", "thangGhiChiSo"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilityReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maChiSo", length = 36)
    private String readingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loaiDichVu", nullable = false)
    private ServiceType serviceType;

    @Column(name = "thangGhiChiSo", nullable = false)
    private LocalDate readingMonth;

    @Column(name = "chiSoDauKy", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal previousIndex = BigDecimal.ZERO;

    @Column(name = "chiSoCuoiKy", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal currentIndex = BigDecimal.ZERO;

    @Column(name = "luongTieuThu", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal consumption = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiGhiSo")
    private Staff recordedBy;

    @CreationTimestamp
    @Column(name = "thoiGianGhi", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    @PreUpdate
    public void calculateConsumption() {
        if (this.currentIndex != null && this.previousIndex != null) {
            this.consumption = this.currentIndex.subtract(this.previousIndex);
        } else if (this.consumption == null) {
            this.consumption = BigDecimal.ZERO;
        }
    }
}
