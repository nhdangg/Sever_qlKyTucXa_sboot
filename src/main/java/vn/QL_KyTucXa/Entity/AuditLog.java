package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "nhatKyHeThong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maNhatKy", length = 36)
    private String logId;

    @PrePersist
    public void ensureIdAndTimestamp() {
        if (this.logId == null || this.logId.isBlank()) {
            this.logId = java.util.UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = java.time.LocalDateTime.now();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung")
    private User user;

    @Column(name = "hanhDong", length = 50, nullable = false)
    private String action;

    @Column(name = "tenBang", length = 100)
    private String tableName;

    @Column(name = "maBanGhi", length = 36)
    private String recordId;

    @Column(name = "giaTriCu", columnDefinition = "json")
    private String oldValue;

    @Column(name = "giaTriMoi", columnDefinition = "json")
    private String newValue;

    @Column(name = "diaChiIp", length = 45)
    private String ipAddress;

    @Column(name = "thongTinTrinhDuyet", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "thoiGianTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
