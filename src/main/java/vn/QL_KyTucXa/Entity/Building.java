package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "toaNha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maToaNha", length = 36)
    private String buildingId;

    @Column(name = "maSoToaNha", length = 20, nullable = false, unique = true)
    private String buildingCode;

    @Column(name = "tenToaNha", length = 100, nullable = false)
    private String buildingName;

    @Column(name = "diaChi", length = 255)
    private String address;

    @Column(name = "soTang", nullable = false)
    @Builder.Default
    private Integer numFloors = 1;

    @Column(name = "loaiGioiTinh", length = 20, nullable = false)
    @Builder.Default
    private String genderType = "MIXED";

    @Column(name = "moTa", length = 255)
    private String description;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
