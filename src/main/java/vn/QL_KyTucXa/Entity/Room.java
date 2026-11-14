package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "phong", uniqueConstraints = {
    @UniqueConstraint(name = "uq_floor_room", columnNames = {"tang", "soPhong"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maPhong", length = 36)
    private String roomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tang", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loaiPhong", nullable = false)
    private RoomType roomType;

    @Column(name = "soPhong", length = 20, nullable = false)
    private String roomNumber;

    @Column(name = "sucChua", nullable = false)
    private Integer capacity;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "AVAILABLE";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
