package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokenDatLaiMatKhau")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maToken", length = 36)
    private String resetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung", nullable = false)
    private User user;

    @Column(name = "chuoiToken", length = 255, nullable = false, unique = true)
    private String token;

    @Column(name = "thoiGianHetHan", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "daSuDung", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @CreationTimestamp
    @Column(name = "thoiGianTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
