package vn.QL_KyTucXa.DTO.Response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilitySplit_Response {
    private String roomId;
    private String roomNumber;
    private String serviceTypeId;
    private String serviceName;
    private LocalDate readingMonth;
    private BigDecimal previousIndex;
    private BigDecimal currentIndex;
    private BigDecimal consumption;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private int activeResidentCount;
    private BigDecimal amountPerResident;
    private List<ResidentShare> residentShares;

    public BigDecimal getAmountPerStudent() {
        return amountPerResident;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResidentShare {
        private String studentId;
        private String studentCode;
        private String fullName;
        private String contractId;
        private String contractCode;
        private BigDecimal shareAmount;
    }
}

