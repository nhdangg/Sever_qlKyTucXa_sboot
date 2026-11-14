package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokenDangXuat", indexes = {
    @Index(name = "idx_logout_tokens_expires", columnList = "thoiGianHetHan")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutToken {

    @Id
    @Column(name = "maToken", length = 36)
    private String tokenId;

    @Column(name = "chuoiToken", length = 512, nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung")
    private User user;

    @Column(name = "thoiGianHetHan", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "thongTinThietBi", length = 255)
    private String deviceInfo;

    @Column(name = "diaChiIp", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "thoiGianDangXuat", nullable = false, updatable = false)
    private LocalDateTime logoutAt;
}
