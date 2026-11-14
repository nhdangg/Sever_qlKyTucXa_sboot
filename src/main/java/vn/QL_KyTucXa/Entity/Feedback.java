package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "phanHoi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maPhanHoi", length = 36)
    private String feedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVien", nullable = false)
    private Student student;

    @Column(name = "danhMuc", length = 50)
    private String category;

    @Column(name = "noiDung", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "danhGiaSao")
    private Integer rating;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "SUBMITTED";

    @Column(name = "phanHoiTraLoi", columnDefinition = "TEXT")
    private String response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiTraLoi")
    private Staff respondedBy;

    @Column(name = "thoiGianTraLoi")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
