package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.SystemSetting_Request;
import vn.QL_KyTucXa.DTO.Response.SystemSetting_Response;
import vn.QL_KyTucXa.Entity.SystemSetting;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.SystemSetting_Mapper;
import vn.QL_KyTucXa.Repository.SystemSetting_Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSetting_ServiceTest {

    @Mock
    private SystemSetting_Repository settingRepository;

    @Mock
    private SystemSetting_Mapper settingMapper;

    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private SystemSetting_Service settingService;

    @Test
    @DisplayName("Khởi tạo cache nạp toàn bộ cấu hình từ CSDL vào bộ nhớ đệm")
    void initSettingsCache_loadsAllFromDatabase() {
        when(settingRepository.findAll()).thenReturn(List.of(
                SystemSetting.builder().settingKey("INVOICE_PAYMENT_DEADLINE_DAYS").settingValue("10").build(),
                SystemSetting.builder().settingKey("LATE_PAYMENT_FINE_PERCENT").settingValue("5.0").build()
        ));

        settingService.initSettingsCache();

        assertThat(settingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 5)).isEqualTo(10);
        assertThat(settingService.getDecimalSetting("LATE_PAYMENT_FINE_PERCENT", BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("Lưu hoặc cập nhật cài đặt hợp lệ thành công")
    void saveOrUpdate_validSetting_success() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("INVOICE_PAYMENT_DEADLINE_DAYS")
                .settingValue("15")
                .description("Hạn thanh toán 15 ngày")
                .build();

        SystemSetting setting = SystemSetting.builder()
                .settingKey("INVOICE_PAYMENT_DEADLINE_DAYS")
                .settingValue("15")
                .description("Hạn thanh toán 15 ngày")
                .build();

        SystemSetting_Response response = SystemSetting_Response.builder()
                .settingKey("INVOICE_PAYMENT_DEADLINE_DAYS")
                .settingValue("15")
                .description("Hạn thanh toán 15 ngày")
                .build();

        when(settingRepository.findById("INVOICE_PAYMENT_DEADLINE_DAYS")).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(SystemSetting.class))).thenReturn(setting);
        when(settingMapper.toResponse(setting)).thenReturn(response);

        SystemSetting_Response result = settingService.saveOrUpdate(request);

        assertThat(result).isNotNull();
        assertThat(result.getSettingValue()).isEqualTo("15");
        assertThat(settingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)).isEqualTo(15);
    }

    @Test
    @DisplayName("Tạo mới cấu hình linh hoạt khi chưa tồn tại trong CSDL")
    void saveOrUpdate_newDynamicKey_success() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("CAMPUS_WIFI_SSID")
                .settingValue("KTX_STUDENT_FREE_5G")
                .description("Tên mạng wifi công cộng")
                .build();

        SystemSetting setting = SystemSetting.builder()
                .settingKey("CAMPUS_WIFI_SSID")
                .settingValue("KTX_STUDENT_FREE_5G")
                .description("Tên mạng wifi công cộng")
                .build();

        when(settingRepository.findById("CAMPUS_WIFI_SSID")).thenReturn(Optional.empty());
        when(settingRepository.save(any(SystemSetting.class))).thenReturn(setting);
        when(settingMapper.toResponse(setting)).thenReturn(SystemSetting_Response.builder().settingKey("CAMPUS_WIFI_SSID").settingValue("KTX_STUDENT_FREE_5G").build());

        SystemSetting_Response response = settingService.saveOrUpdate(request);
        assertThat(response).isNotNull();
        assertThat(response.getSettingKey()).isEqualTo("CAMPUS_WIFI_SSID");
        assertThat(settingService.getSettingValue("CAMPUS_WIFI_SSID", "")).isEqualTo("KTX_STUDENT_FREE_5G");
    }

    @Test
    @DisplayName("Lưu cấu hình thất bại khi tên khóa để trống")
    void saveOrUpdate_blankKey_throwsException() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("   ")
                .settingValue("123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> settingService.saveOrUpdate(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SETTING_KEY_BLANK);
    }

    @Test
    @DisplayName("Lưu cấu hình thất bại khi giá trị để trống")
    void saveOrUpdate_blankValue_throwsException() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("SOME_KEY")
                .settingValue("   ")
                .build();

        AppException ex = assertThrows(AppException.class, () -> settingService.saveOrUpdate(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SETTING_VALUE_BLANK);
    }

    @Test
    @DisplayName("Tạo khóa thất bại khi tên khóa chứa ký tự đặc biệt không hợp lệ")
    void saveOrUpdate_invalidKeyName_throwsException() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("WIFI@ROOM!#$")
                .settingValue("123456")
                .build();

        AppException ex = assertThrows(AppException.class, () -> settingService.saveOrUpdate(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Chặn lưu trữ thông tin bí mật trong tên khóa cấu hình")
    void saveOrUpdate_forbiddenSecretKey_throwsException() {
        SystemSetting_Request request = SystemSetting_Request.builder()
                .settingKey("TELEGRAM_BOT_TOKEN")
                .settingValue("123456:ABC-DEF")
                .build();

        AppException ex = assertThrows(AppException.class, () -> settingService.saveOrUpdate(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Chặn lưu trữ giá trị có dấu hiệu của JWT token hoặc bcrypt password hash")
    void saveOrUpdate_forbiddenSecretValue_throwsException() {
        SystemSetting_Request requestJwt = SystemSetting_Request.builder()
                .settingKey("CUSTOM_ANNOUNCEMENT")
                .settingValue("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.fakeToken")
                .build();

        AppException ex1 = assertThrows(AppException.class, () -> settingService.saveOrUpdate(requestJwt));
        assertThat(ex1.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);

        SystemSetting_Request requestHash = SystemSetting_Request.builder()
                .settingKey("CUSTOM_ANNOUNCEMENT")
                .settingValue("$2a$10$e8w.bZ3Fh3j67u.FakeHashValue")
                .build();

        AppException ex2 = assertThrows(AppException.class, () -> settingService.saveOrUpdate(requestHash));
        assertThat(ex2.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Admin xóa bất kỳ cấu hình nào trong CSDL thành công và dọn dẹp khỏi cache")
    void delete_existingKey_success() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey("INVOICE_PAYMENT_DEADLINE_DAYS")
                .settingValue("10")
                .build();

        when(settingRepository.findById("INVOICE_PAYMENT_DEADLINE_DAYS"))
                .thenReturn(Optional.of(setting))
                .thenReturn(Optional.empty());

        settingService.delete("INVOICE_PAYMENT_DEADLINE_DAYS");

        verify(settingRepository).delete(setting);
        assertThat(settingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)).isEqualTo(10);
    }

    @Test
    @DisplayName("Xóa cấu hình thất bại khi khóa không tồn tại")
    void delete_notFound_throwsException() {
        when(settingRepository.findById("UNKNOWN_KEY")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> settingService.delete("UNKNOWN_KEY"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Tra cứu cài đặt theo khóa thành công")
    void getByKey_success() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey("DORM_NAME")
                .settingValue("Ký túc xá")
                .build();

        when(settingRepository.findById("DORM_NAME")).thenReturn(Optional.of(setting));
        when(settingMapper.toResponse(setting)).thenReturn(SystemSetting_Response.builder().settingKey("DORM_NAME").build());

        SystemSetting_Response res = settingService.getByKey("DORM_NAME");
        assertThat(res).isNotNull();
        assertThat(res.getSettingKey()).isEqualTo("DORM_NAME");
    }

    @Test
    @DisplayName("Tra cứu cài đặt theo khóa thất bại khi không tìm thấy")
    void getByKey_notFound_throwsException() {
        when(settingRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> settingService.getByKey("UNKNOWN"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy toàn bộ danh sách cài đặt hệ thống")
    void getAll_success() {
        List<SystemSetting> list = List.of(
                SystemSetting.builder().settingKey("K1").settingValue("V1").build(),
                SystemSetting.builder().settingKey("K2").settingValue("V2").build()
        );

        when(settingRepository.findAll()).thenReturn(list);
        when(settingMapper.toResponseList(list)).thenReturn(List.of(
                SystemSetting_Response.builder().settingKey("K1").build(),
                SystemSetting_Response.builder().settingKey("K2").build()
        ));

        List<SystemSetting_Response> result = settingService.getAll();
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getIntSetting trả về giá trị mặc định khi giá trị không thể parse sang số")
    void getIntSetting_invalidNumber_returnsDefault() {
        when(settingRepository.findById("INVALID_INT_KEY")).thenReturn(Optional.of(
                SystemSetting.builder().settingKey("INVALID_INT_KEY").settingValue("not_a_number").build()
        ));

        int val = settingService.getIntSetting("INVALID_INT_KEY", 20);
        assertThat(val).isEqualTo(20);
    }

    @Test
    @DisplayName("getDecimalSetting trả về giá trị mặc định khi giá trị không thể parse sang số thực")
    void getDecimalSetting_invalidDecimal_returnsDefault() {
        when(settingRepository.findById("INVALID_DEC_KEY")).thenReturn(Optional.of(
                SystemSetting.builder().settingKey("INVALID_DEC_KEY").settingValue("not_decimal").build()
        ));

        BigDecimal val = settingService.getDecimalSetting("INVALID_DEC_KEY", new BigDecimal("3.5"));
        assertThat(val).isEqualByComparingTo(new BigDecimal("3.5"));
    }

    @Test
    @DisplayName("getBooleanSetting trả về giá trị boolean tương ứng")
    void getBooleanSetting_success() {
        when(settingRepository.findById("ENABLE_FEATURE")).thenReturn(Optional.of(
                SystemSetting.builder().settingKey("ENABLE_FEATURE").settingValue("true").build()
        ));

        boolean val = settingService.getBooleanSetting("ENABLE_FEATURE", false);
        assertThat(val).isTrue();
    }
}
