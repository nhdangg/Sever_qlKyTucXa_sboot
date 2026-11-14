package vn.QL_KyTucXa.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TelegramPollingService {

    final TelegramProperties telegramProperties;
    final TelegramService telegramService;
    final LoginVerificationService loginVerificationService;
    final RestTemplate restTemplate;
    final ObjectMapper objectMapper = new ObjectMapper();

    int lastUpdateId = 0;
    boolean isInitialized = false;
    boolean isLoggedNotConfigured = false;

    /**
     * Đồng bộ lastUpdateId khi khởi động bot (bỏ qua bản tin cũ tích tụ trong 24h khi server offline)
     */
    private void initializeLastUpdateId() {
        try {
            // Dùng offset=-1 để lấy bản tin mới nhất từ Telegram
            String initUrl = String.format("https://api.telegram.org/bot%s/getUpdates?offset=-1&limit=1&timeout=0",
                    telegramProperties.getToken());
            String responseStr = restTemplate.getForObject(initUrl, String.class);
            if (responseStr != null) {
                JsonNode root = objectMapper.readTree(responseStr);
                if (root.path("ok").asBoolean()) {
                    JsonNode result = root.path("result");
                    if (result.isArray() && !result.isEmpty()) {
                        int latestUpdateId = result.get(result.size() - 1).path("update_id").asInt();
                        lastUpdateId = latestUpdateId + 1;
                        // Gửi xác nhận đến Telegram với offset mới nhất để Telegram dọn sạch hàng đợi cũ
                        String ackUrl = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&limit=1&timeout=0",
                                telegramProperties.getToken(), lastUpdateId);
                        restTemplate.getForObject(ackUrl, String.class);
                        log.info("[TELEGRAM BOT] Đã đồng bộ lastUpdateId = {} (bỏ qua bản tin backlog khi khởi động)", lastUpdateId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể đồng bộ lastUpdateId khởi đầu từ Telegram: {}", telegramService.sanitizeError(e.getMessage()));
        }
        isInitialized = true;
    }

    /**
     * Tiến trình chạy ngầm nhận phản hồi từ Telegram bằng Long Polling chuẩn
     */
    @Scheduled(fixedDelayString = "${telegram.bot.polling-interval-ms:1000}")
    public void pollTelegramUpdates() {
        if (!telegramProperties.isConfigured() || !telegramProperties.isPollingEnabled()) {
            if (!isLoggedNotConfigured) {
                log.info("[TELEGRAM 2FA] Telegram Bot chưa được kích hoạt hoặc chưa cấu hình token. Tiến trình polling tạm nghỉ.");
                isLoggedNotConfigured = true;
            }
            return;
        }

        isLoggedNotConfigured = false;

        // Bỏ qua các cập nhật cũ tồn đọng khi ứng dụng khởi động lại
        if (!isInitialized) {
            initializeLastUpdateId();
        }

        int timeout = telegramProperties.getPollingTimeoutSeconds() > 0 ? telegramProperties.getPollingTimeoutSeconds() : 25;
        String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=%d",
                telegramProperties.getToken(), lastUpdateId, timeout);

        try {
            String responseStr = restTemplate.getForObject(url, String.class);
            if (responseStr == null) return;

            JsonNode root = objectMapper.readTree(responseStr);
            if (!root.path("ok").asBoolean()) return;

            JsonNode result = root.path("result");
            if (result.isArray()) {
                for (JsonNode update : result) {
                    int updateId = update.path("update_id").asInt();
                    lastUpdateId = Math.max(lastUpdateId, updateId + 1);

                    // 1. Xử lý khi Admin bấm nút Inline Keyboard: [✅ Đúng] hoặc [❌ Sai]
                    if (update.has("callback_query")) {
                        handleCallbackQuery(update.path("callback_query"));
                    }

                    // 2. Xử lý khi Admin nhắn /start hoặc /myid vào bot để lấy Chat ID
                    if (update.has("message")) {
                        handleIncomingMessage(update.path("message"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra bản tin Telegram: {}", telegramService.sanitizeError(e.getMessage()));
        }
    }

    private void handleCallbackQuery(JsonNode callbackQuery) {
        String callbackQueryId = callbackQuery.path("id").asText();
        String data = callbackQuery.path("data").asText();
        String chatId = callbackQuery.path("message").path("chat").path("id").asText();
        int messageId = callbackQuery.path("message").path("message_id").asInt();
        String fromId = callbackQuery.path("from").path("id").asText();
        String fromUsername = callbackQuery.path("from").path("username").asText("");

        log.info("Nhận callback query từ Telegram: ID={}, Data={}, ChatID={}, FromID={}",
                callbackQueryId, data, chatId, fromId);

        boolean isApprove = data.startsWith("APPROVE:");
        boolean isReject = data.startsWith("REJECT:");

        if (!isApprove && !isReject) {
            return;
        }

        String verificationId = isApprove ? data.substring(8) : data.substring(7);

        // 1. Đối chiếu chatId với session.telegramChatId
        LoginVerificationSession session;
        try {
            session = loginVerificationService.getSession(verificationId);
        } catch (Exception e) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Yêu cầu xác thực không tồn tại hoặc đã hết hạn", true);
            return;
        }

        String sessionChatId = session.getTelegramChatId();
        if (sessionChatId != null && !sessionChatId.isBlank() && !sessionChatId.equals(chatId)) {
            log.warn("Cảnh báo bảo mật: Callback query từ chatId={} không khớp với session chatId={}", chatId, sessionChatId);
            telegramService.answerCallbackQuery(callbackQueryId, "⛔ Thao tác bị từ chối: Đoạn chat không khớp với phiên yêu cầu", true);
            return;
        }

        // 2. Kiểm tra thẩm quyền người bấm nút (fromId)
        // Nếu chat cá nhân (chatId > 0): người bấm phải là chính chủ tài khoản chat đó
        // Nếu chat nhóm (chatId < 0): chỉ người dùng cấu hình adminChatId hoặc chính chủ sở hữu session được bấm
        String configuredAdminChatId = telegramProperties.getAdminChatId();
        boolean isAuthorized = false;
        if (chatId != null && !chatId.startsWith("-")) {
            isAuthorized = chatId.equals(fromId);
        } else {
            isAuthorized = (configuredAdminChatId != null && configuredAdminChatId.equals(fromId))
                    || (sessionChatId != null && sessionChatId.equals(fromId));
        }

        if (!isAuthorized) {
            log.warn("Cảnh báo bảo mật: User fromId={} (@{}) không có quyền duyệt phiên {} trong chat {}",
                    fromId, fromUsername, verificationId, chatId);
            telegramService.answerCallbackQuery(callbackQueryId, "⛔ Bạn không có quyền thao tác trên thông báo này!", true);
            return;
        }

        // 3. Tiến hành phê duyệt hoặc từ chối
        if (isApprove) {
            try {
                loginVerificationService.approve(verificationId);
                telegramService.answerCallbackQuery(callbackQueryId, "✅ Đã phê duyệt đăng nhập thành công!", false);
                telegramService.editMessageText(chatId, messageId,
                        "<b>✅ BẠN ĐÃ PHÊ DUYỆT ĐĂNG NHẬP THÀNH CÔNG!</b>\n\n" +
                        "Hệ thống đã cấp quyền đăng nhập cho tài khoản Quản trị viên.");
            } catch (Exception e) {
                log.error("Lỗi khi phê duyệt đăng nhập từ Telegram: {}", e.getMessage(), e);
                telegramService.answerCallbackQuery(callbackQueryId, "⚠️ " + e.getMessage(), true);
            }
        } else {
            try {
                loginVerificationService.reject(verificationId);
                telegramService.answerCallbackQuery(callbackQueryId, "❌ Đã từ chối yêu cầu đăng nhập!", false);
                telegramService.editMessageText(chatId, messageId,
                        "<b>❌ BẠN ĐÃ TỪ CHỐI YÊU CẦU ĐĂNG NHẬP!</b>\n\n" +
                        "<i>Nếu đây không phải thao tác của bạn, vui lòng đổi mật khẩu ngay lập tức.</i>");
            } catch (Exception e) {
                log.error("Lỗi khi từ chối đăng nhập từ Telegram: {}", e.getMessage(), e);
                telegramService.answerCallbackQuery(callbackQueryId, "⚠️ " + e.getMessage(), true);
            }
        }
    }

    private void handleIncomingMessage(JsonNode messageNode) {
        String text = messageNode.path("text").asText();
        String chatId = messageNode.path("chat").path("id").asText();
        String firstName = messageNode.path("from").path("first_name").asText();

        if ("/start".equalsIgnoreCase(text) || "/myid".equalsIgnoreCase(text)) {
            String safeName = TelegramService.escapeHtml(firstName);
            String safeChatId = TelegramService.escapeHtml(chatId);
            String reply = String.format(
                    "👋 Xin chào <b>%s</b>!\n\n" +
                    "Chat ID Telegram của bạn là: <code>%s</code>\n\n" +
                    "👉 Bạn hãy copy Chat ID này vào file <code>application.yaml</code> " +
                    "ở mục <code>telegram.bot.admin-chat-id</code> để nhận cảnh báo đăng nhập 2FA nhé!",
                    safeName, safeChatId
            );
            telegramService.sendMessage(chatId, reply);
            log.info("Đã phản hồi Chat ID ({}) cho người dùng '{}'", safeChatId, safeName);
        }
    }
}
