package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tang", uniqueConstraints = {
    @UniqueConstraint(name = "uq_building_floor", columnNames = {"toaNha", "soThuTuTang"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maTang", length = 36)
    private String floorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toaNha", nullable = false)
    private Building building;

    @Column(name = "soThuTuTang", nullable = false)
    private Integer floorNumber;

    @Column(name = "moTa", length = 255)
    private String description;
}
