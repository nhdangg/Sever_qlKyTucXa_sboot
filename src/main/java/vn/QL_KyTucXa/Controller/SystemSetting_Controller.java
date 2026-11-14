package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.SystemSetting_Request;
import vn.QL_KyTucXa.DTO.Response.SystemSetting_Response;
import vn.QL_KyTucXa.Service.SystemSetting_Service;

import java.util.List;

@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemSetting_Controller {

    SystemSetting_Service settingService;

    // Lưu hoặc cập nhật cài đặt: Chỉ ADMIN được phép sửa
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<SystemSetting_Response> saveOrUpdateSetting(@RequestBody @Valid SystemSetting_Request request) {
        return Api_Response.<SystemSetting_Response>builder()
                .result(settingService.saveOrUpdate(request))
                .build();
    }

    // Tra cứu cài đặt theo khóa
    @GetMapping("/{settingKey}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<SystemSetting_Response> getSettingByKey(@PathVariable String settingKey) {
        return Api_Response.<SystemSetting_Response>builder()
                .result(settingService.getByKey(settingKey))
                .build();
    }

    // Lấy toàn bộ danh sách cài đặt
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<SystemSetting_Response>> getAllSettings() {
        return Api_Response.<List<SystemSetting_Response>>builder()
                .result(settingService.getAll())
                .build();
    }

    // Nạp lại bộ nhớ đệm cấu hình hệ thống từ CSDL: Chỉ ADMIN được phép
    @PostMapping("/reload-cache")
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<String> reloadCache() {
        settingService.initSettingsCache();
        return Api_Response.<String>builder()
                .result("Đã nạp lại bộ nhớ đệm cấu hình hệ thống thành công")
                .build();
    }

    // Xóa cài đặt: Chỉ ADMIN được phép xóa (chặn xóa các khóa hệ thống cố định trong Service)
    @DeleteMapping("/{settingKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<String> deleteSetting(@PathVariable String settingKey) {
        settingService.delete(settingKey);
        return Api_Response.<String>builder()
                .result(settingKey)
                .build();
    }
}
