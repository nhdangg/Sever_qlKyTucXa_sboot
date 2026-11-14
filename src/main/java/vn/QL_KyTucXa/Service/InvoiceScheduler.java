package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Job định kỳ quét và cập nhật trạng thái hóa đơn quá hạn
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceScheduler {

    Invoice_Service invoiceService;

    // Chạy định kỳ hàng ngày lúc 01:00 AM để quét và cập nhật các hóa đơn đã quá hạn
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduleOverdueProcessing() {
        invoiceService.processOverdueInvoices();
    }
}

