package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "khachTham")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Visitor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maKhachTham", length = 36)
    private String visitorId;

    @Column(name = "tenKhachTham", length = 100, nullable = false)
    private String visitorName;

    @Column(name = "soCccd", length = 20)
    private String nationalId;

    @Column(name = "soDienThoai", length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinhVienDonTiep", nullable = false)
    private Student hostStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phong", nullable = false)
    private Room room;

    @Column(name = "mucDich", length = 255)
    private String purpose;

    @Column(name = "thoiGianVao", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "thoiGianRa")
    private LocalDateTime checkOutTime;

    @Column(name = "anhCccd", length = 255)
    private String idCardImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDuyet")
    private Staff approver;
}
