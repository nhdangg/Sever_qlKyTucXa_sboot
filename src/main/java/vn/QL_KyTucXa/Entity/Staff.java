package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nhanVien")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maNhanVien", length = 36)
    private String staffId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung", nullable = false, unique = true)
    private User user;

    @Column(name = "maSoNhanVien", length = 20, nullable = false, unique = true)
    private String staffCode;

    @Column(name = "hoTen", length = 100, nullable = false)
    private String fullName;

    @Column(name = "chucVu", length = 100)
    private String position;

    @Column(name = "phongBan", length = 100)
    private String department;

    @Column(name = "soDienThoai", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "ngayVaoLam")
    private LocalDate hireDate;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
