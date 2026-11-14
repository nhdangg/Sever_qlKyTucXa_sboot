package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dotDangKy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maDotDangKy", length = 36)
    private String periodId;

    @Column(name = "tenDotDangKy", length = 100, nullable = false)
    private String periodName;

    @Column(name = "namHoc", length = 20, nullable = false)
    private String academicYear;

    @Column(name = "ngayBatDau", nullable = false)
    private LocalDate startDate;

    @Column(name = "ngayKetThuc", nullable = false)
    private LocalDate endDate;

    @Column(name = "hanDangKy")
    private LocalDate registrationDeadline;

    @Column(name = "trangThai", length = 20, nullable = false)
    @Builder.Default
    private String status = "UPCOMING";

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
