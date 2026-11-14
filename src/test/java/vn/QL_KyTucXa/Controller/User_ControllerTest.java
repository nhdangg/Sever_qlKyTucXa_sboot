package vn.QL_KyTucXa.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.QL_KyTucXa.DTO.Request.UserSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Exception.GlobalExceptionHandler;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.UserProfileService;
import vn.QL_KyTucXa.Service.User_Service;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class User_ControllerTest {

        private MockMvc mockMvc;

        @Mock
        private User_Service userService;

        @Mock
        private CurrentUser_Service currentUserService;

        @Mock
        private UserProfileService userProfileService;

        @InjectMocks
        private User_Controller userController;

        private final ObjectMapper objectMapper = new ObjectMapper();

        private User_Request request;
        private User_Response response;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(userController)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();

                request = User_Request.builder()
                                .username("john_doe")
                                .password("secret123")
                                .email("john@example.com")
                                .phone("0912345678")
                                .roleId("role-01")
                                .status("ACTIVE")
                                .build();

                response = User_Response.builder()
                                .userId("user-01")
                                .username("john_doe")
                                .email("john@example.com")
                                .phone("0912345678")
                                .status("ACTIVE")
                                .build();
        }

        @Test
        @DisplayName("POST /api/users - Tạo tài khoản thành công")
        void createUser_success() throws Exception {
                when(userService.create(any(User_Request.class))).thenReturn(response);

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.username").value("john_doe"))
                                .andExpect(jsonPath("$.result.email").value("john@example.com"));
        }

        @Test
        @DisplayName("GET /api/users/{userId} - Lấy chi tiết user thành công")
        void getUserById_success() throws Exception {
                when(userService.getById("user-01")).thenReturn(response);

                mockMvc.perform(get("/api/users/user-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.userId").value("user-01"));
        }

        @Test
        @DisplayName("GET /api/users/{userId} - Không tìm thấy user trả về HTTP 404")
        void getUserById_notFound() throws Exception {
                when(userService.getById("unknown-id")).thenThrow(new AppException(ErrorCode.USER_NOT_EXISTED));

                mockMvc.perform(get("/api/users/unknown-id"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value(1003));
        }

        @Test
        @DisplayName("GET /api/users - Lấy danh sách tất cả user")
        void getAllUsers_success() throws Exception {
                when(userService.getAll()).thenReturn(List.of(response));

                mockMvc.perform(get("/api/users"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result[0].username").value("john_doe"));
        }

        @Test
        @DisplayName("PUT /api/users/{userId} - Cập nhật user thành công")
        void updateUser_success() throws Exception {
                when(userService.update(eq("user-01"), any(User_Request.class))).thenReturn(response);

                mockMvc.perform(put("/api/users/user-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100));
        }

        @Test
        @DisplayName("DELETE /api/users/{userId} - Xóa user thành công")
        void deleteUser_success() throws Exception {
                doNothing().when(userService).delete("user-01");

                mockMvc.perform(delete("/api/users/user-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result").value("user-01"));
        }

        @Test
        @DisplayName("GET /api/users/me - Lấy thông tin tài khoản hiện tại")
        void getMyProfile_success() throws Exception {
                when(currentUserService.userId()).thenReturn("user-01");
                when(userService.getById("user-01")).thenReturn(response);

                mockMvc.perform(get("/api/users/me"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.username").value("john_doe"));
        }

        @Test
        @DisplayName("GET /api/users/me/profile - Lấy hồ sơ vai trò hiện tại")
        void getMyRoleProfile_success() throws Exception {
                UserProfileDTO profile = UserProfileDTO.builder()
                                .roleCode("STUDENT")
                                .roleName("Sinh viên")
                                .fullName("Nguyen Van A")
                                .userCode("SV2026001")
                                .build();

                when(currentUserService.userId()).thenReturn("user-01");
                when(userProfileService.resolveUserProfileByUserId("user-01")).thenReturn(profile);

                mockMvc.perform(get("/api/users/me/profile"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.fullName").value("Nguyen Van A"))
                                .andExpect(jsonPath("$.result.userCode").value("SV2026001"));
        }

        @Test
        @DisplayName("PUT /api/users/me - Người dùng tự cập nhật thông tin")
        void updateMyProfile_success() throws Exception {
                UserSelfUpdate_Request selfRequest = UserSelfUpdate_Request.builder()
                                .email("newjohn@example.com")
                                .phone("0988776655")
                                .password("newpass123")
                                .build();

                when(currentUserService.userId()).thenReturn("user-01");
                when(userService.updateSelf(eq("user-01"), any(UserSelfUpdate_Request.class))).thenReturn(response);

                mockMvc.perform(put("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(selfRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100));
        }

        @Test
        @DisplayName("PATCH /api/users/me/2fa - Cập nhật Telegram Chat ID cho chính mình")
        void updateMy2FA_success() throws Exception {
                when(currentUserService.userId()).thenReturn("user-01");
                when(userService.updateTelegram2FA("user-01", "123456789")).thenReturn(response);

                mockMvc.perform(patch("/api/users/me/2fa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("telegramChatId", "123456789"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100));
        }

        @Test
        @DisplayName("PATCH /api/users/{userId}/2fa - Admin cập nhật Telegram Chat ID cho người dùng")
        void updateUser2FA_success() throws Exception {
                when(userService.updateTelegram2FA("user-02", "987654321")).thenReturn(response);

                mockMvc.perform(patch("/api/users/user-02/2fa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("telegramChatId", "987654321"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100));
        }
}
