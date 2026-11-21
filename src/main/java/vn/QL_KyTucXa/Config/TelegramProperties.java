package vn.QL_KyTucXa.Config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TelegramProperties {

    /**
     * Token lấy từ @BotFather trên Telegram
     */
    String token = "";

    /**
     * Chat ID của Admin (lấy từ @userinfobot hoặc khi gõ /start vào bot)
     */
    String adminChatId = "";

    public String getToken() {
        if (token != null) {
            return token.replace("\"", "").replace("'", "").trim();
        }
        return "";
    }

    public String getAdminChatId() {
        if (adminChatId != null) {
            return adminChatId.replace("\"", "").replace("'", "").trim();
        }
        return "";
    }

    public String getBackupCode() {
        if (backupCode != null) {
            return backupCode.replace("\"", "").replace("'", "").trim();
        }
        return "KTX@Emergency2FA2026";
    }

    /**
     * Bật/tắt tiến trình Long Polling nhận tương tác nút bấm
     */
    boolean pollingEnabled = true;

    /**
     * Chu kỳ nghỉ giữa các lần gọi long-polling (mili-giây)
     */
    long pollingIntervalMs = 1000;

    /**
     * Thời gian Telegram giữ kết nối chờ bản tin mới (Long Polling, giây)
     */
    int pollingTimeoutSeconds = 25;

    /**
     * Địa chỉ backend (nếu có dùng cho link xác nhận)
     */
    String serverUrl = "http://localhost:8912/QLyKyTucXa";

    /**
     * Mã dự phòng khẩn cấp khi Telegram gặp sự cố
     */
    String backupCode = "KTX@Emergency2FA2026";

    public boolean isConfigured() {
        return token != null && !token.isBlank() && !token.startsWith("your_");
    }
}
