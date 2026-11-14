package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cauHinhHeThong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "khoaCauHinh", length = 100, nullable = false)
    private String settingKey;

    @Column(name = "giaTriCauHinh", length = 500, nullable = false)
    private String settingValue;

    @Column(name = "moTa", length = 255)
    private String description;

    @UpdateTimestamp
    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime updatedAt;
}
