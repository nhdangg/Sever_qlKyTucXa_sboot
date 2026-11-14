package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "quyen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maQuyen", length = 36)
    private String permissionId;

    @Column(name = "maCodeQuyen", length = 100, nullable = false, unique = true)
    private String permissionCode;

    @Column(name = "phanHe", length = 50, nullable = false)
    private String module;

    @Column(name = "moTa", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
