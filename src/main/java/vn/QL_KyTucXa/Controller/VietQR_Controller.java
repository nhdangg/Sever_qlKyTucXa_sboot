package vn.QL_KyTucXa.Controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Request.SepayWebhook_Request;
import vn.QL_KyTucXa.Service.Payment_Service;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vietqr")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VietQR_Controller {

    Payment_Service paymentService;

    /**
     * Webhook nhận biến động số dư VietQR / SePay (VietinBank / MB / VCB)
     * Endpoint: POST /api/vietqr/webhook (hoặc /api/vietqr/sepay-webhook)
     */
    @PostMapping({"/webhook", "/sepay-webhook"})
    public ResponseEntity<Map<String, Object>> handleVietQrWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) SepayWebhook_Request request
    ) {
        log.info("Nhận Webhook từ cổng thanh toán VietQR / SePay: {}", request);
        if (!paymentService.validateSepayWebhookToken(authorizationHeader)) {
            log.warn("Sai API Key xác thực Webhook: {}", authorizationHeader);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Sai API Key xác thực Webhook"));
        }

        Map<String, Object> result = paymentService.processSepayWebhook(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Hỗ trợ GET /api/vietqr/webhook để kiểm tra endpoint hoạt động (Health check qua trình duyệt)
     */
    @GetMapping({"/webhook", "/sepay-webhook"})
    public ResponseEntity<Map<String, Object>> checkWebhookHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Cổng Webhook VietQR / SePay đang hoạt động bình thường",
                "method", "Vui lòng sử dụng phương thức POST để gửi biến động số dư"
        ));
    }
}

