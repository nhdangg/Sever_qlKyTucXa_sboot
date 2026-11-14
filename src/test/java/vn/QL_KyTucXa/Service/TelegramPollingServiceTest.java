package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramPollingServiceTest {

    @Mock
    private TelegramProperties telegramProperties;

    @Mock
    private TelegramService telegramService;

    @Mock
    private LoginVerificationService loginVerificationService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TelegramPollingService pollingService;

    @BeforeEach
    void setUp() {
        lenient().when(telegramProperties.getToken()).thenReturn("test-token");
        lenient().when(telegramProperties.getPollingTimeoutSeconds()).thenReturn(25);
    }

    @Test
    @DisplayName("Bỏ qua polling khi bot chưa cấu hình hoặc polling bị tắt")
    void pollTelegramUpdates_disabled_doesNothing() {
        when(telegramProperties.isConfigured()).thenReturn(false);

        pollingService.pollTelegramUpdates();

        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("Khởi tạo và đồng bộ lastUpdateId bằng offset=-1 khi khởi động")
    void pollTelegramUpdates_initializesWithNegativeOffset() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        // Giả lập phản hồi khi gọi offset=-1
        String initResponse = "{\"ok\":true,\"result\":[{\"update_id\":500}]}";
        String pollResponse = "{\"ok\":true,\"result\":[]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn(initResponse);
        when(restTemplate.getForObject(contains("offset=501"), eq(String.class))).thenReturn(pollResponse);

        pollingService.pollTelegramUpdates();

        // Xác nhận đã gọi offset=-1 và sau đó gọi với offset=501
        verify(restTemplate).getForObject(contains("offset=-1"), eq(String.class));
        verify(restTemplate).getForObject(contains("offset=501&limit=1"), eq(String.class));
        verify(restTemplate).getForObject(contains("offset=501&timeout=25"), eq(String.class));
    }

    @Test
    @DisplayName("Từ chối callback query khi chatId không khớp với session.telegramChatId")
    void handleCallbackQuery_chatMismatch_rejects() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        String updateJson = "{\"ok\":true,\"result\":[{" +
                "\"update_id\":100," +
                "\"callback_query\":{" +
                "\"id\":\"cq-1\"," +
                "\"from\":{\"id\":999999,\"first_name\":\"Attacker\"}," +
                "\"message\":{\"message_id\":10,\"chat\":{\"id\":999999}}," +
                "\"data\":\"APPROVE:session-01\"" +
                "}}]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn("{\"ok\":true,\"result\":[]}");
        when(restTemplate.getForObject(contains("timeout=25"), eq(String.class))).thenReturn(updateJson);

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("session-01")
                .telegramChatId("111222") // Chat ID hợp lệ là 111222, khác 999999
                .build();
        when(loginVerificationService.getSession("session-01")).thenReturn(session);

        pollingService.pollTelegramUpdates();

        verify(telegramService).answerCallbackQuery(eq("cq-1"), contains("không khớp"), eq(true));
        verify(loginVerificationService, never()).approve(anyString());
    }

    @Test
    @DisplayName("Từ chối callback query khi người bấm khác chủ sở hữu trong chat cá nhân")
    void handleCallbackQuery_unauthorizedUserInPrivateChat_rejects() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        String updateJson = "{\"ok\":true,\"result\":[{" +
                "\"update_id\":101," +
                "\"callback_query\":{" +
                "\"id\":\"cq-2\"," +
                "\"from\":{\"id\":888888,\"first_name\":\"Imposter\"}," +
                "\"message\":{\"message_id\":11,\"chat\":{\"id\":111222}}," +
                "\"data\":\"APPROVE:session-02\"" +
                "}}]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn("{\"ok\":true,\"result\":[]}");
        when(restTemplate.getForObject(contains("timeout=25"), eq(String.class))).thenReturn(updateJson);

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("session-02")
                .telegramChatId("111222")
                .build();
        when(loginVerificationService.getSession("session-02")).thenReturn(session);

        pollingService.pollTelegramUpdates();

        verify(telegramService).answerCallbackQuery(eq("cq-2"), contains("không có quyền"), eq(true));
        verify(loginVerificationService, never()).approve(anyString());
    }

    @Test
    @DisplayName("Phê duyệt đăng nhập thành công khi đúng chatId và đúng fromId")
    void handleCallbackQuery_authorizedApprove_success() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        String updateJson = "{\"ok\":true,\"result\":[{" +
                "\"update_id\":102," +
                "\"callback_query\":{" +
                "\"id\":\"cq-3\"," +
                "\"from\":{\"id\":111222,\"first_name\":\"Admin\"}," +
                "\"message\":{\"message_id\":12,\"chat\":{\"id\":111222}}," +
                "\"data\":\"APPROVE:session-03\"" +
                "}}]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn("{\"ok\":true,\"result\":[]}");
        when(restTemplate.getForObject(contains("timeout=25"), eq(String.class))).thenReturn(updateJson);

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("session-03")
                .telegramChatId("111222")
                .build();
        when(loginVerificationService.getSession("session-03")).thenReturn(session);

        pollingService.pollTelegramUpdates();

        verify(loginVerificationService).approve("session-03");
        verify(telegramService).answerCallbackQuery(eq("cq-3"), contains("thành công"), eq(false));
        verify(telegramService).editMessageText(eq("111222"), eq(12), contains("PHÊ DUYỆT ĐĂNG NHẬP THÀNH CÔNG"));
    }

    @Test
    @DisplayName("Từ chối đăng nhập thành công khi nhận callback REJECT")
    void handleCallbackQuery_authorizedReject_success() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        String updateJson = "{\"ok\":true,\"result\":[{" +
                "\"update_id\":103," +
                "\"callback_query\":{" +
                "\"id\":\"cq-4\"," +
                "\"from\":{\"id\":111222,\"first_name\":\"Admin\"}," +
                "\"message\":{\"message_id\":15,\"chat\":{\"id\":111222}}," +
                "\"data\":\"REJECT:session-04\"" +
                "}}]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn("{\"ok\":true,\"result\":[]}");
        when(restTemplate.getForObject(contains("timeout=25"), eq(String.class))).thenReturn(updateJson);

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("session-04")
                .telegramChatId("111222")
                .build();
        when(loginVerificationService.getSession("session-04")).thenReturn(session);

        pollingService.pollTelegramUpdates();

        verify(loginVerificationService).reject("session-04");
        verify(telegramService).answerCallbackQuery(eq("cq-4"), contains("từ chối"), eq(false));
        verify(telegramService).editMessageText(eq("111222"), eq(15), contains("TỪ CHỐI"));
    }

    @Test
    @DisplayName("Xử lý lệnh /start và phản hồi Chat ID đã được escape an toàn")
    void handleIncomingMessage_startCommand_repliesSafely() {
        when(telegramProperties.isConfigured()).thenReturn(true);
        when(telegramProperties.isPollingEnabled()).thenReturn(true);

        String updateJson = "{\"ok\":true,\"result\":[{" +
                "\"update_id\":104," +
                "\"message\":{" +
                "\"text\":\"/start\"," +
                "\"chat\":{\"id\":777888}," +
                "\"from\":{\"first_name\":\"<Admin>&Team\"}" +
                "}}]}";

        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class))).thenReturn("{\"ok\":true,\"result\":[]}");
        when(restTemplate.getForObject(contains("timeout=25"), eq(String.class))).thenReturn(updateJson);

        pollingService.pollTelegramUpdates();

        verify(telegramService).sendMessage(eq("777888"), contains("&lt;Admin&gt;&amp;Team"));
    }
}
