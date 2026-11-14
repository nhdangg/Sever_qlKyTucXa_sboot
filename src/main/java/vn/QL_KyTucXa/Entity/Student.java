package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sinhVien")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maSinhVien", length = 36)
    private String studentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung", nullable = false, unique = true)
    private User user;

    @Column(name = "maSoSinhVien", length = 20, nullable = false, unique = true)
    private String studentCode;

    @Column(name = "hoTen", length = 100, nullable = false)
    private String fullName;

    @Column(name = "ngaySinh")
    private LocalDate dob;

    @Column(name = "gioiTinh", length = 10, nullable = false)
    private String gender;

    @Column(name = "soCccd", length = 20, unique = true)
    private String nationalId;

    @Column(name = "khoa", length = 100)
    private String faculty;

    @Column(name = "lop", length = 50)
    private String className;

    @Column(name = "queQuan", length = 255)
    private String hometown;

    @Column(name = "diaChiThuongTru", length = 255)
    private String permanentAddress;

    @Column(name = "soDienThoai", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "duongDanAnh", length = 255)
    private String avatarUrl;

    @Column(name = "tenNguoiThanKhanCap", length = 100)
    private String emergencyContactName;

    @Column(name = "sdtNguoiThanKhanCap", length = 20)
    private String emergencyContactPhone;

    @Column(name = "quanHeNguoiThan", length = 50)
    private String emergencyContactRelation;

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
