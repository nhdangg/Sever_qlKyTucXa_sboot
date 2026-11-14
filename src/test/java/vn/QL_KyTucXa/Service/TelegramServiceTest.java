package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private TelegramProperties telegramProperties;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Environment environment;

    @InjectMocks
    private TelegramService telegramService;

    private LoginVerificationSession sampleSession;

    @BeforeEach
    void setUp() {
        sampleSession = LoginVerificationSession.builder()
                .verificationId("session-xyz-123")
                .userId("user-01")
                .username("admin<script>")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0 <CustomBot>&Tools")
                .status("PENDING")
                .telegramChatId("123456789")
                .createdAt(LocalDateTime.of(2026, 11, 6, 10, 30, 0))
                .build();
    }

    @Test
    @DisplayName("escapeHtml chuyển đổi các ký tự &, <, > sang thực thể HTML an toàn")
    void escapeHtml_sanitizesSpecialCharacters() {
        assertThat(TelegramService.escapeHtml("admin<test>&\"user\""))
                .isEqualTo("admin&lt;test&gt;&amp;\"user\"");
        assertThat(TelegramService.escapeHtml(null)).isEmpty();
    }

    @Test
    @DisplayName("sanitizeError che giấu bot token để không bị lộ trong log")
    void sanitizeError_hidesToken() {
        when(telegramProperties.getToken()).thenReturn("SECRET_BOT_TOKEN_123");
        String errorMsg = "Error connecting to https://api.telegram.org/botSECRET_BOT_TOKEN_123/sendMessage";

        String sanitized = telegramService.sanitizeError(errorMsg);

        assertThat(sanitized).doesNotContain("SECRET_BOT_TOKEN_123");
        assertThat(sanitized).contains("******");
    }

    @Test
    @DisplayName("Gửi thông báo xác thực thành công và nội dung HTML đã được escape an toàn")
    void sendLoginVerificationMessage_escapesContentAndReturnsMessageId() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("fake-token");

        String fakeResponse = "{\"ok\":true,\"result\":{\"message_id\":888}}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(fakeResponse);

        Integer msgId = telegramService.sendLoginVerificationMessage(sampleSession, "123456789");

        assertThat(msgId).isEqualTo(888);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(contains("/sendMessage"), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).isNotNull();
        String text = (String) body.get("text");
        assertThat(text).contains("admin&lt;script&gt;");
        assertThat(text).contains("Mozilla/5.0 &lt;CustomBot&gt;&amp;Tools");
        assertThat(text).doesNotContain("<script>");
    }

    @Test
    @DisplayName("Telegram API trả về ok=false khi gửi thông báo thì ném AppException")
    void sendLoginVerificationMessage_apiError_throwsAppException() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("fake-token");

        String fakeErrorResponse = "{\"ok\":false,\"description\":\"Bad Request: chat not found\"}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(fakeErrorResponse);

        AppException ex = assertThrows(AppException.class, () ->
                telegramService.sendLoginVerificationMessage(sampleSession, "123456789"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chat not found");
    }

    @Test
    @DisplayName("RestTemplate ném ngoại lệ mạng thì ném AppException và che token")
    void sendLoginVerificationMessage_networkException_throwsAppException() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("SECRET_TOKEN");

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection timed out to botSECRET_TOKEN"));

        AppException ex = assertThrows(AppException.class, () ->
                telegramService.sendLoginVerificationMessage(sampleSession, "123456789"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).doesNotContain("SECRET_TOKEN");
        assertThat(ex.getMessage()).contains("******");
    }

    @Test
    @DisplayName("Khi chưa cấu hình bot ở môi trường dev thì ghi log và trả về null")
    void sendLoginVerificationMessage_unconfigured_devProfile_returnsNull() {
        when(telegramProperties.isConfigured()).thenReturn(false);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        Integer result = telegramService.sendLoginVerificationMessage(sampleSession, null);
        assertThat(result).isNull();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("Khi chưa cấu hình bot ở môi trường production thì ném AppException")
    void sendLoginVerificationMessage_unconfigured_prodProfile_throwsException() {
        when(telegramProperties.isConfigured()).thenReturn(false);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                telegramService.sendLoginVerificationMessage(sampleSession, null));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chưa được cấu hình");
    }

    @Test
    @DisplayName("answerCallbackQuery xử lý ngoại lệ an toàn và không làm gián đoạn chương trình")
    void answerCallbackQuery_handlesExceptionSafely() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("tok");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Network down"));

        assertDoesNotThrow(() -> telegramService.answerCallbackQuery("cb-123", "Hello", false));
    }

    @Test
    @DisplayName("editMessageText xử lý ngoại lệ an toàn và không ném lỗi ra ngoài")
    void editMessageText_handlesExceptionSafely() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("tok");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Network down"));

        assertDoesNotThrow(() -> telegramService.editMessageText("123", 456, "New text"));
    }

    @Test
    @DisplayName("sendMessage xử lý ngoại lệ an toàn và không ném lỗi ra ngoài")
    void sendMessage_handlesExceptionSafely() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.getToken()).thenReturn("tok");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Network down"));

        assertDoesNotThrow(() -> telegramService.sendMessage("123", "Hello"));
    }
}

