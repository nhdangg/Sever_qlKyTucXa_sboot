package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.ResetPassword_Request;
import vn.QL_KyTucXa.DTO.Request.UserSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Request.UserStatus_Request;
import org.springframework.security.core.Authentication;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.UserProfileService;
import vn.QL_KyTucXa.Service.User_Service;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class User_Controller {

        User_Service userService;
        CurrentUser_Service currentUserService;
        UserProfileService userProfileService;

        @GetMapping("/me")
        public Api_Response<User_Response> getMyProfile() {
                String currentUserId = currentUserService.userId();
                return Api_Response.<User_Response>builder()
                                .result(userService.getById(currentUserId))
                                .build();
        }

        @GetMapping({"/me/profile", "/profile"})
        public Api_Response<UserProfileDTO> getMyRoleProfile() {
                String currentUserId = currentUserService.userId();
                return Api_Response.<UserProfileDTO>builder()
                                .result(userProfileService.resolveUserProfileByUserId(currentUserId))
                                .build();
        }

        @PutMapping("/me")
        public Api_Response<User_Response> updateMyProfile(@RequestBody @Valid UserSelfUpdate_Request request) {
                String currentUserId = currentUserService.userId();
                return Api_Response.<User_Response>builder()
                                .result(userService.updateSelf(currentUserId, request))
                                .build();
        }

        @PostMapping("/me/change-password")
        public Api_Response<String> changePassword(@RequestBody @Valid vn.QL_KyTucXa.DTO.Request.ChangePassword_Request request) {
                String currentUserId = currentUserService.userId();
                userService.changePassword(currentUserId, request);
                return Api_Response.<String>builder()
                                .result("Đổi mật khẩu thành công")
                                .build();
        }

        @PatchMapping("/me/2fa")
        public Api_Response<User_Response> updateMy2FA(@RequestBody Map<String, String> body) {
                String currentUserId = currentUserService.userId();
                String chatId = body != null ? body.get("telegramChatId") : null;
                String password = body != null ? body.get("password") : null;
                User_Response res = (password != null && !password.isBlank())
                                ? userService.updateTelegram2FA(currentUserId, chatId, password)
                                : userService.updateTelegram2FA(currentUserId, chatId);
                return Api_Response.<User_Response>builder()
                                .result(res)
                                .build();
        }

        @PatchMapping("/{userId}/2fa")
        @PreAuthorize("hasRole('ADMIN')")
        public Api_Response<User_Response> updateUser2FA(
                        @PathVariable String userId,
                        @RequestBody Map<String, String> body) {
                String chatId = body != null ? body.get("telegramChatId") : null;
                String password = body != null ? body.get("password") : null;
                User_Response res = (password != null && !password.isBlank())
                                ? userService.updateTelegram2FA(userId, chatId, password)
                                : userService.updateTelegram2FA(userId, chatId);
                return Api_Response.<User_Response>builder()
                                .result(res)
                                .build();
        }

        // API Quản trị viên đặt lại mật khẩu cho người dùng khi quên mật khẩu
        @PostMapping("/{userId}/reset-password")
        @PreAuthorize("hasRole('ADMIN')")
        public Api_Response<String> resetPassword(
                        @PathVariable String userId,
                        @RequestBody(required = false) @Valid ResetPassword_Request request) {
                return Api_Response.<String>builder()
                                .result(userService.resetPassword(userId, request))
                                .build();
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
        public Api_Response<User_Response> createUser(@RequestBody @Valid User_Request request) {
                return Api_Response.<User_Response>builder()
                                .result(userService.create(request))
                                .build();
        }

        @GetMapping("/{userId}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<User_Response> getUserById(@PathVariable String userId) {
                return Api_Response.<User_Response>builder()
                                .result(userService.getById(userId))
                                .build();
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<List<User_Response>> getAllUsers() {
                return Api_Response.<List<User_Response>>builder()
                                .result(userService.getAll())
                                .build();
        }

        @GetMapping("/search")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<vn.QL_KyTucXa.DTO.Response.Page_Response<User_Response>> searchUsers(
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String roleId,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return Api_Response.<vn.QL_KyTucXa.DTO.Response.Page_Response<User_Response>>builder()
                                .result(userService.searchUsers(keyword, roleId, status, page, size))
                                .build();
        }

        @PutMapping("/{userId}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<User_Response> updateUser(
                        @PathVariable String userId,
                        @RequestBody @Valid User_Request request) {
                return Api_Response.<User_Response>builder()
                                .result(userService.update(userId, request))
                                .build();
        }

        @PatchMapping("/{userId}/status")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<User_Response> updateUserStatus(
                        @PathVariable String userId,
                        @RequestBody @Valid UserStatus_Request request,
                        Authentication authentication) {
                boolean callerIsAdmin = authentication != null && authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                return Api_Response.<User_Response>builder()
                                .result(userService.updateStatus(userId, request.getStatus(), callerIsAdmin))
                                .build();
        }

        @PutMapping("/{userId}/status")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAnyAuthority('USER_MANAGE', 'USER_LOCK')")
        public Api_Response<User_Response> updateUserStatusPut(
                        @PathVariable String userId,
                        @RequestBody @Valid UserStatus_Request request,
                        Authentication authentication) {
                return updateUserStatus(userId, request, authentication);
        }

        @DeleteMapping("/{userId}")
        @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
        public Api_Response<String> deleteUser(@PathVariable String userId) {
                userService.delete(userId);
                return Api_Response.<String>builder()
                                .result(userId)
                                .build();
        }
}
