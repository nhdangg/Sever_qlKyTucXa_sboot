package vn.QL_KyTucXa.Service;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.SystemSetting_Request;
import vn.QL_KyTucXa.DTO.Response.SystemSetting_Response;
import vn.QL_KyTucXa.Entity.SystemSetting;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.SystemSetting_Mapper;
import vn.QL_KyTucXa.Repository.SystemSetting_Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemSetting_Service {

    SystemSetting_Repository settingRepository;
    SystemSetting_Mapper settingMapper;

    // Bộ nhớ đệm thread-safe để tăng tốc độ đọc cấu hình thường xuyên
    ConcurrentMap<String, String> settingCache = new ConcurrentHashMap<>();

    // Danh sách từ khóa nhạy cảm cấm lưu trữ trong cấu hình hệ thống
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "PASSWORD", "SECRET", "TOKEN", "CREDENTIAL", "PRIVATE_KEY", "AUTH_KEY", "API_KEY", "ACCESS_KEY");

    // Mẫu regex kiểm tra tên khóa cấu hình hợp lệ (chữ hoa, số, gạch dưới)
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z0-9_]{2,100}$");

    // Mẫu regex kiểm tra định dạng thời gian HH:mm (ví dụ: 07:00, 21:30)
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");

    // Khởi tạo và nạp toàn bộ cấu hình từ CSDL vào bộ nhớ đệm
    @PostConstruct
    public void initSettingsCache() {
        try {
            List<SystemSetting> tat_ca_cai_dat = settingRepository.findAll();
            for (SystemSetting cai_dat : tat_ca_cai_dat) {
                if (cai_dat.getSettingKey() != null && cai_dat.getSettingValue() != null) {
                    settingCache.put(cai_dat.getSettingKey().toUpperCase(), cai_dat.getSettingValue());
                }
            }
            log.info("Đã nạp thành công {} cấu hình hệ thống vào Cache", settingCache.size());
        } catch (Exception e) {
            log.warn("Không thể khởi tạo cache cấu hình hệ thống: {}", e.getMessage());
        }
    }

    // Lưu hoặc cập nhật cài đặt hệ thống (Chỉ ADMIN được sửa)
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSetting_Response saveOrUpdate(SystemSetting_Request request) {
        if (request == null || request.getSettingKey() == null || request.getSettingKey().isBlank()) {
            throw new AppException(ErrorCode.SETTING_KEY_BLANK);
        }
        if (request.getSettingValue() == null || request.getSettingValue().isBlank()) {
            throw new AppException(ErrorCode.SETTING_VALUE_BLANK);
        }

        String rawKey = request.getSettingKey().trim().toUpperCase();
        String rawValue = request.getSettingValue().trim();
        String description = request.getDescription() != null ? request.getDescription().trim() : null;

        // 1. Chặn lưu trữ bí mật (token, password, key bí mật)
        validateNoSecrets(rawKey, rawValue);

        // 2. Kiểm tra định dạng khóa và kiểu dữ liệu giá trị tương ứng
        validateSettingKeyAndValue(rawKey, rawValue);

        log.info("Lưu hoặc cập nhật cài đặt hệ thống: {} = {}", rawKey, rawValue);

        SystemSetting cai_dat = settingRepository.findById(rawKey).orElse(null);
        if (cai_dat == null) {
            cai_dat = SystemSetting.builder()
                    .settingKey(rawKey)
                    .settingValue(rawValue)
                    .description(description)
                    .build();
        } else {
            cai_dat.setSettingValue(rawValue);
            if (description != null && !description.isBlank()) {
                cai_dat.setDescription(description);
            }
        }

        cai_dat = settingRepository.save(cai_dat);

        // 3. Cập nhật cache đồng bộ
        settingCache.put(rawKey, rawValue);

        return settingMapper.toResponse(cai_dat);
    }

    // Tra cứu cài đặt theo khóa
    @Transactional(readOnly = true)
    public SystemSetting_Response getByKey(String settingKey) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new AppException(ErrorCode.SETTING_KEY_BLANK);
        }
        String key = settingKey.trim().toUpperCase();
        SystemSetting cai_dat = settingRepository.findById(key)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cấu hình: " + key));

        return settingMapper.toResponse(cai_dat);
    }

    // Lấy toàn bộ danh sách cài đặt hệ thống (không cần phân trang/tìm kiếm)
    @Transactional(readOnly = true)
    public List<SystemSetting_Response> getAll() {
        List<SystemSetting> danh_sach_cai_dat = settingRepository.findAll();
        return settingMapper.toResponseList(danh_sach_cai_dat);
    }

    // Xóa cài đặt hệ thống (Chỉ ADMIN được xóa)
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String settingKey) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new AppException(ErrorCode.SETTING_KEY_BLANK);
        }
        String key = settingKey.trim().toUpperCase();

        SystemSetting cai_dat = settingRepository.findById(key)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cấu hình: " + key));

        settingRepository.delete(cai_dat);
        settingCache.remove(key);
        log.info("Đã xóa thành công cài đặt hệ thống: {}", key);
    }

    // Helper: Đọc giá trị cấu hình dạng chuỗi từ cache
    public String getSetting(String key, String defaultValue) {
        return getSettingValue(key, defaultValue);
    }

    // Helper: Đọc giá trị cấu hình dạng chuỗi từ cache
    public String getSettingValue(String key, String defaultValue) {
        if (key == null || key.isBlank()) {
            return defaultValue;
        }
        String normalizedKey = key.trim().toUpperCase();
        String cached = settingCache.get(normalizedKey);
        if (cached != null) {
            return cached;
        }
        return settingRepository.findById(normalizedKey)
                .map(SystemSetting::getSettingValue)
                .map(val -> {
                    settingCache.put(normalizedKey, val);
                    return val;
                })
                .orElse(defaultValue);
    }

    // Helper: Đọc giá trị cấu hình dạng số nguyên
    public int getIntSetting(String key, int defaultValue) {
        String val = getSettingValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("Không thể parse cấu hình {}='{}' sang int, dùng mặc định {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    // Helper: Đọc giá trị cấu hình dạng số thực BigDecimal
    public BigDecimal getDecimalSetting(String key, BigDecimal defaultValue) {
        String val = getSettingValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(val.trim());
        } catch (Exception e) {
            log.warn("Không thể parse cấu hình {}='{}' sang BigDecimal, dùng mặc định {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    // Helper: Đọc giá trị cấu hình dạng Boolean
    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String val = getSettingValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }

    // Kiểm tra và ngăn chặn lưu trữ bí mật (token, mật khẩu, private key)
    private void validateNoSecrets(String key, String value) {
        for (String forbidden : FORBIDDEN_WORDS) {
            if (key.contains(forbidden)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không được phép lưu thông tin bảo mật, mật khẩu hoặc token tại cấu hình hệ thống: " + key);
            }
        }
        // Kiểm tra xem giá trị có dấu hiệu của JWT, bcrypt hash hoặc PEM key không
        if (value.startsWith("eyJ") && value.contains(".")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không được phép lưu chuỗi token bảo mật tại cấu hình hệ thống");
        }
        if (value.startsWith("$2a$") || value.startsWith("$2b$") || value.contains("BEGIN PRIVATE KEY")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không được phép lưu mật khẩu đã băm hoặc khóa mã hóa tại cấu hình hệ thống");
        }
    }

    // Kiểm tra tính hợp lệ của tên khóa và giá trị tương ứng
    private void validateSettingKeyAndValue(String key, String value) {
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "Tên khóa cấu hình chỉ được chứa chữ cái in hoa, số và dấu gạch dưới (VD: INVOICE_PAYMENT_DEADLINE_DAYS, WIFI_PASS)");
        }
        if (value.length() > 500) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Độ dài giá trị cấu hình tối đa 500 ký tự");
        }

        // 1. Kiểm tra định dạng thời gian (HH:mm) cho các key giờ giấc
        if (key.endsWith("_HOUR") || key.endsWith("_TIME") || key.contains("VISIT_HOUR")) {
            if (!TIME_PATTERN.matcher(value).matches()) {
                throw new AppException(ErrorCode.INVALID_INPUT,
                        "Giá trị cấu hình thời gian cho khóa '" + key + "' phải có định dạng HH:mm (ví dụ 21:30, 07:00)");
            }
        }

        // 2. Kiểm tra số nguyên dương cho các key ngày tháng / giới hạn / lần thử
        if (key.endsWith("_DAYS") || key.endsWith("_HOURS") || key.endsWith("_LIMIT")
                || key.endsWith("_ATTEMPTS") || key.endsWith("_COUNT") || key.startsWith("MAX_LOGIN_")) {
            try {
                int val = Integer.parseInt(value);
                if (val <= 0) {
                    throw new AppException(ErrorCode.INVALID_INPUT,
                            "Giá trị cấu hình cho khóa '" + key + "' phải là số nguyên dương lớn hơn 0");
                }
            } catch (NumberFormatException e) {
                throw new AppException(ErrorCode.INVALID_INPUT,
                        "Giá trị cấu hình cho khóa '" + key + "' phải là số nguyên hợp lệ");
            }
        }

        // 3. Kiểm tra số tiền / tỷ lệ cho các key tiền tệ
        if (key.endsWith("_AMOUNT") || key.endsWith("_FEE") || key.endsWith("_PRICE") || key.endsWith("_RATE")) {
            try {
                BigDecimal val = new BigDecimal(value);
                if (val.compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.INVALID_INPUT,
                            "Giá trị cấu hình tiền tệ cho khóa '" + key + "' không được nhỏ hơn 0");
                }
            } catch (Exception e) {
                throw new AppException(ErrorCode.INVALID_INPUT,
                        "Giá trị cấu hình cho khóa '" + key + "' phải là số tiền hợp lệ");
            }
        }

        // 4. Kiểm tra giá trị Boolean cho các cờ bật/tắt
        if (key.startsWith("ENABLE_") || key.startsWith("ALLOW_") || key.startsWith("AUTO_") || key.startsWith("IS_")) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new AppException(ErrorCode.INVALID_INPUT,
                        "Giá trị cấu hình cờ '" + key + "' chỉ nhận 'true' hoặc 'false'");
            }
        }
    }
}
