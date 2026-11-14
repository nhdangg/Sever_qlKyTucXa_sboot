package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vaiTroQuyen")
@IdClass(RolePermissionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @Column(name = "maVaiTro", length = 36)
    private String roleId;

    @Id
    @Column(name = "maQuyen", length = 36)
    private String permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maVaiTro", insertable = false, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maQuyen", insertable = false, updatable = false)
    private Permission permission;
}
