package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokenLamMoi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maToken", length = 36)
    private String tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung", nullable = false)
    private User user;

    @Column(name = "chuoiToken", length = 512, nullable = false, unique = true)
    private String token;

    @Column(name = "thongTinThietBi", length = 255)
    private String deviceInfo;

    @Column(name = "diaChiIp", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "thoiGianCap", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "thoiGianHetHan", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "daThuHoi", nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    @Column(name = "thoiGianThuHoi")
    private LocalDateTime revokedAt;
}
