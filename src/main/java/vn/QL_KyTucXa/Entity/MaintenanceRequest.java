package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "yeuCauSuaChua")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maYeuCau", length = 36)
    private String requestId;

    @Column(name = "maSoYeuCau", length = 30, nullable = false, unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiBaoCao", nullable = false)
    private User reportedBy;

    @Column(name = "danhMuc", length = 50)
    private String category;

    @Column(name = "moTa", length = 500, nullable = false)
    private String description;

    @Column(name = "mucDoUuTien", length = 20, nullable = false)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiPhuTrach")
    private Staff assignedTo;

    @CreationTimestamp
    @Column(name = "ngayYeuCau", nullable = false, updatable = false)
    private LocalDateTime requestedDate;

    @Column(name = "ngayHoanThanh")
    private LocalDateTime completedDate;

    @Column(name = "ghiChuXuLy", length = 500)
    private String resolutionNote;
}
