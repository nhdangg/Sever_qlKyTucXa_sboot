package vn.QL_KyTucXa.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelegramService {

    TelegramProperties telegramProperties;
    RestTemplate restTemplate;
    Environment environment;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    // Escape ký tự đặc biệt theo chuẩn HTML của Telegram Bot API (&, <, >)
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // Che giấu bot token trong chuỗi thông báo lỗi để tránh rò rỉ vào log
    public String sanitizeError(String msg) {
        if (msg == null) return "Unknown error";
        String token = telegramProperties.getToken();
        if (token != null && !token.isBlank()) {
            return msg.replace(token, "******");
        }
        return msg;
    }

    // Gửi tin nhắn xác thực đăng nhập 2FA kèm 2 nút bấm 'Đúng' và 'Sai'
    public Integer sendLoginVerificationMessage(LoginVerificationSession session, String chatId) {
        String targetChatId = (chatId != null && !chatId.isBlank()) ? chatId : telegramProperties.getAdminChatId();

        // Nhánh dự phòng khi chưa cấu hình bot hoặc chat id
        if (!telegramProperties.isConfigured() || targetChatId == null || targetChatId.isBlank()) {
            boolean isDev = environment.acceptsProfiles(Profiles.of("dev", "test", "default"));
            log.warn("[TELEGRAM 2FA] Chưa cấu hình TELEGRAM_BOT_TOKEN hoặc TELEGRAM_ADMIN_CHAT_ID!");
            if (isDev) {
                log.warn("Tài khoản '{}' đang chờ xác thực 2FA với mã: {}", session.getUsername(), session.getVerificationId());
                return null;
            } else {
                log.warn("Tài khoản '{}' yêu cầu xác thực 2FA nhưng Telegram chưa được cấu hình trên môi trường production", session.getUsername());
                throw new AppException(ErrorCode.OPERATION_FAILED, "Dịch vụ xác thực 2FA qua Telegram chưa được cấu hình trên hệ thống");
            }
        }

        String url = String.format("https://api.telegram.org/bot%s/sendMessage", telegramProperties.getToken());

        String timeStr = session.getCreatedAt() != null ? session.getCreatedAt().format(FORMATTER) : "";
        String text = String.format(
                "🛡️ <b>[BẢO MẬT KÝ TÚC XÁ] XÁC THỰC ĐĂNG NHẬP ADMIN</b> 🛡️\n\n" +
                "Xin chào Quản trị viên,\n" +
                "Hệ thống vừa nhận được yêu cầu đăng nhập tài khoản:\n" +
                "👤 Tài khoản: <b>%s</b>\n" +
                "🕒 Thời gian: <b>%s</b>\n" +
                "🌐 Địa chỉ IP: <code>%s</code>\n" +
                "🖥️ Thiết bị: <i>%s</i>\n\n" +
                "❓ <b>Bạn đang muốn đăng nhập vào hệ thống phải không?</b>\n" +
                "Vui lòng nhấn xác nhận:",
                escapeHtml(session.getUsername()),
                escapeHtml(timeStr),
                escapeHtml(session.getIpAddress()),
                escapeHtml(session.getUserAgent())
        );

        // Tạo 2 nút bấm Inline Keyboard: [✅ Đúng] và [❌ Sai]
        Map<String, Object> buttonApprove = new HashMap<>();
        buttonApprove.put("text", "✅ Đúng (Đồng ý đăng nhập)");
        buttonApprove.put("callback_data", "APPROVE:" + session.getVerificationId());

        Map<String, Object> buttonReject = new HashMap<>();
        buttonReject.put("text", "❌ Sai (Từ chối đăng nhập)");
        buttonReject.put("callback_data", "REJECT:" + session.getVerificationId());

        List<Map<String, Object>> row = List.of(buttonApprove, buttonReject);
        List<List<Map<String, Object>>> keyboard = List.of(row);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", keyboard);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", targetChatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", replyMarkup);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String responseStr = restTemplate.postForObject(url, request, String.class);
            JsonNode root = objectMapper.readTree(responseStr);

            if (root.path("ok").asBoolean()) {
                int messageId = root.path("result").path("message_id").asInt();
                log.info("Đã gửi thông báo Telegram thành công đến Chat ID={}, Message ID={}", targetChatId, messageId);
                return messageId;
            } else {
                String errorDesc = root.path("description").asText("Lỗi Telegram API không xác định");
                log.error("Telegram API trả về lỗi: {}", errorDesc);
                throw new AppException(ErrorCode.OPERATION_FAILED, "Gửi xác thực Telegram thất bại: " + errorDesc);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            String safeError = sanitizeError(e.getMessage());
            log.error("Lỗi khi gửi thông báo xác thực tới Telegram: {}", safeError);
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể kết nối đến máy chủ Telegram: " + safeError);
        }
    }

    // Phản hồi sự kiện nhấn nút trên Telegram (CallbackQuery)
    public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        if (!telegramProperties.isConfigured() || callbackQueryId == null || callbackQueryId.isBlank()) return;

        String url = String.format("https://api.telegram.org/bot%s/answerCallbackQuery", telegramProperties.getToken());
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        body.put("text", text != null ? text : "");
        body.put("show_alert", showAlert);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn("Không thể phản hồi CallbackQuery {}: {}", callbackQueryId, sanitizeError(e.getMessage()));
        }
    }

    // Chỉnh sửa nội dung tin nhắn sau khi Admin đã bấm (ẩn nút bấm đi để tránh bấm nhiều lần)
    public void editMessageText(String chatId, Integer messageId, String newText) {
        if (!telegramProperties.isConfigured() || chatId == null || messageId == null) return;

        String url = String.format("https://api.telegram.org/bot%s/editMessageText", telegramProperties.getToken());
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", newText != null ? newText : "");
        body.put("parse_mode", "HTML");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn("Không thể sửa tin nhắn Telegram (Chat ID={}, Message ID={}): {}",
                    chatId, messageId, sanitizeError(e.getMessage()));
        }
    }

    // Gửi tin nhắn thông thường (ví dụ chào mừng hoặc báo Chat ID)
    public void sendMessage(String chatId, String text) {
        if (!telegramProperties.isConfigured() || chatId == null || chatId.isBlank()) return;

        String url = String.format("https://api.telegram.org/bot%s/sendMessage", telegramProperties.getToken());
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text != null ? text : "");
        body.put("parse_mode", "HTML");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn("Không thể gửi tin nhắn Telegram tới Chat ID {}: {}", chatId, sanitizeError(e.getMessage()));
        }
    }
}
