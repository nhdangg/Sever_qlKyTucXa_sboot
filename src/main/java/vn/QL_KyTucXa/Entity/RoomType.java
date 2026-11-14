package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loaiPhong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maLoaiPhong", length = 36)
    private String roomTypeId;

    @Column(name = "tenLoaiPhong", length = 50, nullable = false, unique = true)
    private String typeName;

    @Column(name = "sucChua", nullable = false)
    private Integer capacity;

    @Column(name = "dienTichM2", precision = 6, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "giaThueHangThang", precision = 12, scale = 2, nullable = false)
    private BigDecimal pricePerMonth;

    @Column(name = "moTa", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
