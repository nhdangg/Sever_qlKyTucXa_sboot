package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "taiSanPhong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maTaiSanPhong", length = 36)
    private String roomAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taiSan", nullable = false)
    private Asset asset;

    @Column(name = "soLuong", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "ghiChuTinhTrang", length = 255)
    private String conditionNote;

    @Column(name = "ngayBanGiao", nullable = false)
    private LocalDate assignedDate;
}
