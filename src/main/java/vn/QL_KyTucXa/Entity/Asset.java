package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "taiSan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maTaiSan", length = 36)
    private String assetId;

    @Column(name = "maSoTaiSan", length = 30, nullable = false, unique = true)
    private String assetCode;

    @Column(name = "tenTaiSan", length = 100, nullable = false)
    private String assetName;

    @Column(name = "danhMucTaiSan", length = 50)
    private String category;

    @Column(name = "donGia", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "ngayMua")
    private LocalDate purchaseDate;

    @Column(name = "hanBaoHanh")
    private LocalDate warrantyUntil;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
