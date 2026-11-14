package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "loaiDichVu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maLoaiDichVu", length = 36)
    private String serviceTypeId;

    @Column(name = "maSoDichVu", length = 20, nullable = false, unique = true)
    private String serviceCode;

    @Column(name = "tenDichVu", length = 100, nullable = false)
    private String serviceName;

    @Column(name = "donViTinh", length = 20)
    private String unit;

    @Column(name = "donGia", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "hinhThucTinhPhi", length = 20, nullable = false)
    @Builder.Default
    private String billingType = "METERED";

    @Column(name = "moTa", length = 255)
    private String description;
}
