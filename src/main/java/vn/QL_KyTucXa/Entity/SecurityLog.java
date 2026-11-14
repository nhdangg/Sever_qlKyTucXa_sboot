package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "nhatKyAnNinh")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maNhatKy", length = 36)
    private String logId;

    @Column(name = "loaiSuKien", length = 30, nullable = false)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong")
    private Room room;

    @Column(name = "moTa", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiGhiNhan")
    private Staff recordedBy;

    @CreationTimestamp
    @Column(name = "thoiGianSuKien", nullable = false, updatable = false)
    private LocalDateTime eventTime;
}
