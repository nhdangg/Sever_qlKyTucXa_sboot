package vn.QL_KyTucXa.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.QL_KyTucXa.DTO.CheckToken_Request;
import vn.QL_KyTucXa.DTO.CheckToken_Response;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Repository.User_Repository;
import vn.QL_KyTucXa.Service.Jwt_Service;

import java.text.ParseException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private Jwt_Service jwtService;

    @Mock
    private User_Repository userRepository;

    @InjectMocks
    private CustomJwtDecoder customJwtDecoder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(customJwtDecoder, "signerKey",
                "d41d8cd98f00b204e9800998ecf8427e068b5a034d610811e2f754388e6e5a4f1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    }

    @Test
    @DisplayName("decode - Token không hợp lệ theo checkToken ném JwtException")
    void decode_invalidToken_throwsJwtException() throws Exception {
        when(jwtService.checkToken(any(CheckToken_Request.class)))
                .thenReturn(CheckToken_Response.builder().checkToken(false).build());

        assertThatThrownBy(() -> customJwtDecoder.decode("invalid-token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Token không hợp lệ hoặc đã hết hạn");
    }

    @Test
    @DisplayName("decode - Lỗi parse/JOSE khi checkToken ném JwtException")
    void decode_parseException_throwsJwtException() throws Exception {
        when(jwtService.checkToken(any(CheckToken_Request.class)))
                .thenThrow(new ParseException("Malformed token", 0));

        assertThatThrownBy(() -> customJwtDecoder.decode("bad-token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Malformed token");
    }

    @Test
    @DisplayName("decode - Tài khoản bị khóa trong thời gian token còn hạn thì ném JwtException ngay lập tức")
    void decode_lockedUser_throwsJwtException() throws Exception {
        when(jwtService.checkToken(any(CheckToken_Request.class)))
                .thenReturn(CheckToken_Response.builder().checkToken(true).build());
        when(jwtService.extractUsername("valid-token")).thenReturn("locked_user");

        User lockedUser = User.builder().username("locked_user").status("LOCKED").build();
        when(userRepository.findByUsername("locked_user")).thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> customJwtDecoder.decode("valid-token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
    }
}
