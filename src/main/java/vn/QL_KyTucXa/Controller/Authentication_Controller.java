package vn.QL_KyTucXa.Controller;

import com.nimbusds.jose.JOSEException;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.CheckToken_Request;
import vn.QL_KyTucXa.DTO.CheckToken_Response;
import vn.QL_KyTucXa.DTO.Request.Authentication_Request;
import vn.QL_KyTucXa.DTO.Request.LogoutToken_Request;
import vn.QL_KyTucXa.DTO.Request.RefreshToken_Request;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.Service.Authentication_Service;
import vn.QL_KyTucXa.Service.Jwt_Service;

import java.text.ParseException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Authentication_Controller {

    Authentication_Service authenticationService;
    Jwt_Service jwtService;

    /**
     * API Đăng nhập hệ thống
     *
     * @param request chứa username và password
     * @return Token_Response chứa access token và refresh token
     */
    @PostMapping("/login")
    public Api_Response<Token_Response> login(@RequestBody @Valid Authentication_Request request) {
        return Api_Response.<Token_Response>builder()
                .result(authenticationService.login(request))
                .build();
    }

    /**
     * API Kiểm tra tính hợp lệ của Token
     */
    @PostMapping("/checkToken")
    public Api_Response<CheckToken_Response> checkToken(@RequestBody @Valid CheckToken_Request request)
            throws JOSEException, ParseException {
        return Api_Response.<CheckToken_Response>builder()
                .result(jwtService.checkToken(request))
                .build();
    }

    /**
     * API Đăng ký tài khoản
     */
    @PostMapping("/register")
    public Api_Response<User_Response> register(@RequestBody @Valid User_Request request) {
        return Api_Response.<User_Response>builder()
                .result(authenticationService.register(request))
                .build();
    }

    /**
     * API Làm mới Access Token từ Refresh Token
     *
     * @param request chứa refreshToken
     * @return Token_Response với access token mới
     */
    @PostMapping("/refresh")
    public Api_Response<Token_Response> refreshToken(@RequestBody @Valid RefreshToken_Request request) {
        return Api_Response.<Token_Response>builder()
                .result(authenticationService.refreshToken(request))
                .build();
    }

    /**
     * API Đăng xuất (Vô hiệu hóa Token)
     *
     * @param request chứa token cần logout
     * @return thông báo thành công
     */
    @PostMapping("/logout")
    public Api_Response<String> logout(@RequestBody @Valid LogoutToken_Request request) {
        authenticationService.logout(request);
        return Api_Response.<String>builder()
                .result("Đăng xuất thành công")
                .build();
    }

    /**
     * API Kiểm tra trạng thái phê duyệt đăng nhập 2FA qua Telegram (Client Polling)
     *
     * @param verificationId mã phiên xác thực nhận được từ API login
     * @return Token_Response chứa Access Token nếu đã duyệt, hoặc trạng thái
     *         PENDING
     */
    @GetMapping("/login-status/{verificationId}")
    public Api_Response<Token_Response> checkLoginStatus(@PathVariable String verificationId) {
        return Api_Response.<Token_Response>builder()
                .result(authenticationService.checkLoginStatus(verificationId))
                .build();
    }

    /**
     * API Phê duyệt / Từ chối xác thực 2FA qua liên kết xác nhận
     *
     * @param id mã phiên xác thực
     * @param action hành động (approve / reject)
     * @return kết quả xử lý
     */
    @GetMapping("/verify-login")
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<String> verifyLogin(
            @RequestParam String id,
            @RequestParam(defaultValue = "approve") String action
    ) {
        return Api_Response.<String>builder()
                .result(authenticationService.confirmLoginFromTelegram(id, action))
                .build();
    }

    /**
     * API Phê duyệt 2FA bằng mã dự phòng khẩn cấp khi dịch vụ Telegram gặp sự cố
     */
    @PostMapping("/verify-backup-code")
    public Api_Response<Token_Response> verifyBackupCode(@RequestBody java.util.Map<String, String> body) {
        String verificationId = body != null ? body.get("verificationId") : null;
        String backupCode = body != null ? body.get("backupCode") : null;
        if (verificationId == null || verificationId.isBlank() || backupCode == null || backupCode.isBlank()) {
            throw new vn.QL_KyTucXa.Exception.AppException(vn.QL_KyTucXa.Exception.ErrorCode.OPERATION_FAILED,
                    "Mã phiên xác thực và mã dự phòng không được để trống");
        }
        return Api_Response.<Token_Response>builder()
                .result(authenticationService.verifyWithBackupCode(verificationId, backupCode))
                .build();
    }
}
