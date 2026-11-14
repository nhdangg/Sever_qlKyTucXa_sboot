package vn.QL_KyTucXa.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "chiTietHoaDon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "maChiTiet", length = 36)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hoaDon", nullable = false)
    private Invoice invoice;

    @Column(name = "loaiKhoanThu", length = 20, nullable = false)
    private String itemType;

    @Column(name = "moTa", length = 255, nullable = false)
    private String description;

    @Column(name = "soLuong", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "donGia", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "thanhTien", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "maThamChieu", length = 36)
    private String referenceId;

    @PrePersist
    @PreUpdate
    public void calculateAmount() {
        if (this.quantity != null && this.unitPrice != null) {
            this.amount = this.quantity.multiply(this.unitPrice);
        } else if (this.amount == null) {
            this.amount = BigDecimal.ZERO;
        }
    }
}
