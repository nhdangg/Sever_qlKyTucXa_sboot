package vn.QL_KyTucXa.DTO.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayWebhook_Request {
    Long id;                    // ID giao dịch trên SePay
    String gateway;             // Tên ngân hàng (VietinBank, MBBank, Vietcombank,...)
    String transactionDate;     // Thời gian giao dịch
    String accountNumber;       // Số tài khoản ngân hàng nhận tiền
    String code;                // Mã code thanh toán của SePay
    String content;             // Nội dung chuyển khoản
    String transferType;        // in (tiền vào) hoặc out (tiền ra)
    BigDecimal transferAmount;  // Số tiền chuyển vào
    BigDecimal accumulated;     // Số dư lũy kế tài khoản
    String subAccount;          // Tài khoản phụ
    String referenceCode;       // Mã tham chiếu ngân hàng
    String description;         // Toàn bộ nội dung biến động số dư
}

