package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "thongBao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maThongBao", length = 36)
    private String notificationId;

    @Column(name = "tieuDe", length = 255, nullable = false)
    private String title;

    @Column(name = "noiDung", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "loaiDoiTuong", length = 20, nullable = false)
    @Builder.Default
    private String targetType = "ALL";

    @Column(name = "maDoiTuong", length = 36)
    private String targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiTao", nullable = false)
    private User createdBy;

    @Column(name = "daPhatHanh", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "thoiGianPhatHanh")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
