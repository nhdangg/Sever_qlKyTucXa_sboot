package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "loaiViPham")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maLoaiViPham", length = 36)
    private String violationTypeId;

    @Column(name = "tenLoaiViPham", length = 150, nullable = false)
    private String violationName;

    @Column(name = "mucDoNghiemTrong", length = 20, nullable = false)
    @Builder.Default
    private String severityLevel = "MINOR";

    @Column(name = "diemTru", nullable = false)
    @Builder.Default
    private Integer penaltyPoints = 0;

    @Column(name = "tienPhat", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Column(name = "moTa", length = 255)
    private String description;
}
