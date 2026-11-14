package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "viPham")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maViPham", length = 36)
    private String violationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loaiViPham", nullable = false)
    private ViolationType violationType;

    @Column(name = "ngayViPham", nullable = false)
    private LocalDate violationDate;

    @Column(name = "moTa", length = 500)
    private String description;

    @Column(name = "hinhThucXuLy", length = 255)
    private String actionTaken;

    @Column(name = "tienPhat", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Column(name = "diemTruApDung", nullable = false)
    @Builder.Default
    private Integer penaltyPointsApplied = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiXuLy")
    private Staff handledBy;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "RECORDED";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
