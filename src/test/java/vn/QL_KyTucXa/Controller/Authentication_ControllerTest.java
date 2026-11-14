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
import vn.QL_KyTucXa.DTO.Request.Authentication_Request;
import vn.QL_KyTucXa.DTO.Request.LogoutToken_Request;
import vn.QL_KyTucXa.DTO.Request.RefreshToken_Request;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Exception.GlobalExceptionHandler;
import vn.QL_KyTucXa.Service.Authentication_Service;
import vn.QL_KyTucXa.Service.Jwt_Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class Authentication_ControllerTest {

        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Mock
        private Authentication_Service authenticationService;

        @Mock
        private Jwt_Service jwtService;

        @InjectMocks
        private Authentication_Controller authenticationController;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(authenticationController)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

        @Test
        @DisplayName("POST /api/auth/login - Đăng nhập thành công trả về Token")
        void login_success() throws Exception {
                Authentication_Request request = Authentication_Request.builder()
                                .username("student01")
                                .password("password123")
                                .build();

                Token_Response tokenResponse = Token_Response.builder()
                                .token("mock_access_token")
                                .refreshToken("mock_refresh_token")
                                .authenticated(true)
                                .role("STUDENT")
                                .build();

                when(authenticationService.login(any(Authentication_Request.class))).thenReturn(tokenResponse);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.token").value("mock_access_token"))
                                .andExpect(jsonPath("$.result.authenticated").value(true));
        }

        @Test
        @DisplayName("POST /api/auth/login - Đăng nhập sai tài khoản trả về HTTP 400")
        void login_wrongCredentials_returnsBadRequest() throws Exception {
                Authentication_Request request = Authentication_Request.builder()
                                .username("student01")
                                .password("wrong_password")
                                .build();

                when(authenticationService.login(any(Authentication_Request.class)))
                                .thenThrow(new AppException(ErrorCode.INVALID_CREDENTIALS));

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value(1006));
        }

        @Test
        @DisplayName("POST /api/auth/refresh - Cấp mới Token thành công")
        void refreshToken_success() throws Exception {
                RefreshToken_Request request = RefreshToken_Request.builder()
                                .token("valid_refresh_token")
                                .build();

                Token_Response tokenResponse = Token_Response.builder()
                                .token("new_access_token")
                                .refreshToken("valid_refresh_token")
                                .authenticated(true)
                                .build();

                when(authenticationService.refreshToken(any(RefreshToken_Request.class))).thenReturn(tokenResponse);

                mockMvc.perform(post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.token").value("new_access_token"));
        }

        @Test
        @DisplayName("POST /api/auth/logout - Đăng xuất thành công")
        void logout_success() throws Exception {
                LogoutToken_Request request = LogoutToken_Request.builder()
                                .token("current_access_token")
                                .build();

                doNothing().when(authenticationService).logout(any(LogoutToken_Request.class));

                mockMvc.perform(post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100));
        }
}
