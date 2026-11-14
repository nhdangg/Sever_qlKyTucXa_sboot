package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Payment_Request;
import vn.QL_KyTucXa.DTO.Request.SepayWebhook_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Payment_Response;
import vn.QL_KyTucXa.Service.Payment_Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Payment_Controller {

        Payment_Service paymentService;

        @PostMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'STUDENT')")
        public Api_Response<Payment_Response> makePayment(@RequestBody @Valid Payment_Request request) {
                return Api_Response.<Payment_Response>builder()
                                .result(paymentService.create(request))
                                .build();
        }

        @PutMapping("/{paymentId}/confirm")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('PAYMENT_MANAGE')")
        public Api_Response<Payment_Response> confirmPayment(
                        @PathVariable String paymentId,
                        @RequestParam(required = false) String staffId,
                        @RequestParam(required = false) String transactionId) {
                return Api_Response.<Payment_Response>builder()
                                .result(paymentService.confirmPayment(paymentId, staffId, transactionId))
                                .build();
        }

        /**
         * Webhook nhận biến động số dư SePay / Ngân hàng
         * Endpoint: POST /api/payments/webhook hoặc /api/payments/sepay-webhook
         */
        @PostMapping({ "/webhook", "/sepay-webhook" })
        public ResponseEntity<Map<String, Object>> handleSepayWebhook(
                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                        @RequestBody(required = false) SepayWebhook_Request request) {
                if (!paymentService.validateSepayWebhookToken(authorizationHeader)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("success", false, "message", "Sai API Key xác thực Webhook"));
                }
                Map<String, Object> result = paymentService.processSepayWebhook(request);
                return ResponseEntity.ok(result);
        }

        /**
         * Kiểm tra trạng thái Webhook qua trình duyệt (Health Check)
         */
        @GetMapping({ "/webhook", "/sepay-webhook" })
        public ResponseEntity<Map<String, Object>> checkWebhookHealth() {
                return ResponseEntity.ok(Map.of(
                                "status", "UP",
                                "message", "Cổng Webhook Payment SePay đang hoạt động bình thường",
                                "method", "Vui lòng sử dụng phương thức POST để gửi biến động số dư"));
        }

        @GetMapping("/{paymentId}")
        @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
        public Api_Response<Payment_Response> getPaymentById(@PathVariable String paymentId) {
                return Api_Response.<Payment_Response>builder()
                                .result(paymentService.getById(paymentId))
                                .build();
        }

        @GetMapping("/invoice/{invoiceId}")
        @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasRole('STUDENT')")
        public Api_Response<List<Payment_Response>> getPaymentsByInvoice(@PathVariable String invoiceId) {
                return Api_Response.<List<Payment_Response>>builder()
                                .result(paymentService.getByInvoiceId(invoiceId))
                                .build();
        }

        @GetMapping
        @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
        public Api_Response<Page_Response<Payment_Response>> getAllPayments(
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size,
                        @RequestParam(value = "invoiceCode", required = false) String invoiceCode,
                        @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                        @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                        @RequestParam(value = "sortBy", defaultValue = "paymentDate") String sortBy,
                        @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
                return Api_Response.<Page_Response<Payment_Response>>builder()
                                .result(paymentService.getAll(page, size, invoiceCode, paymentMethod, status, fromDate,
                                                toDate, sortBy, sortDir))
                                .build();
        }

        @DeleteMapping("/{paymentId}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
        public Api_Response<String> deletePayment(@PathVariable String paymentId) {
                paymentService.delete(paymentId);
                return Api_Response.<String>builder()
                                .result(paymentId)
                                .build();
        }
}
