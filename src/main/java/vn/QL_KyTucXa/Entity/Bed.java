package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "giuong", uniqueConstraints = {
    @UniqueConstraint(name = "uq_room_bed", columnNames = {"phong", "soGiuong"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maGiuong", length = 36)
    private String bedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong", nullable = false)
    private Room room;

    @Column(name = "soGiuong", length = 10, nullable = false)
    private String bedNumber;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "AVAILABLE";
}
