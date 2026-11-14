package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chuyenPhong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maChuyenPhong", length = 36)
    private String transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hopDong", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "giuongCu", nullable = false)
    private Bed oldBed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "giuongMoi", nullable = false)
    private Bed newBed;

    @Column(name = "ngayChuyen", nullable = false)
    private LocalDate transferDate;

    @Column(name = "lyDo", length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDuyet")
    private Staff approver;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
