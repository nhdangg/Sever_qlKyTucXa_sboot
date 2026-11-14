package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "nguoiDung")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maNguoiDung", length = 36)
    private String userId;

    @Column(name = "tenDangNhap", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "matKhau", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    @Column(name = "soDienThoai", length = 20)
    private String phone;

    @Column(name = "idTelegramChat", length = 50)
    private String telegramChatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vaiTro", nullable = false)
    private Role role;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "daXacThucEmail", nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = false;

    @Column(name = "soLanDangNhapSai", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "phaiDoiMatKhau", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;

    @Column(name = "thoiGianDangNhapCuoi")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
