package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vaiTro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maVaiTro", length = 36)
    private String roleId;

    @Column(name = "maCodeVaiTro", length = 50, nullable = false, unique = true)
    private String roleCode;

    @Column(name = "tenVaiTro", length = 100, nullable = false)
    private String roleName;

    @Column(name = "moTa", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
