package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "nguoiNhanThongBao", uniqueConstraints = {
    @UniqueConstraint(name = "uq_notification_user", columnNames = {"thongBao", "nguoiDung"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maNguoiNhan", length = 36)
    private String recipientId;

    @PrePersist
    public void ensureId() {
        if (this.recipientId == null || this.recipientId.isBlank()) {
            this.recipientId = java.util.UUID.randomUUID().toString();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thongBao", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoiDung", nullable = false)
    private User user;

    @Column(name = "daDoc", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "thoiGianDoc")
    private LocalDateTime readAt;
}
