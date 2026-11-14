package vn.QL_KyTucXa.Config;

import com.nimbusds.jose.JOSEException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import vn.QL_KyTucXa.DTO.CheckToken_Request;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Repository.User_Repository;
import vn.QL_KyTucXa.Service.Jwt_Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Objects;
import java.util.Optional;

@Configuration
public class CustomJwtDecoder implements JwtDecoder {

    @Value("${jwt.signer-key}")
    private String signerKey;

    @Autowired
    private Jwt_Service jwtService;

    @Autowired(required = false)
    private User_Repository userRepository;

    private NimbusJwtDecoder nimbusJwtDecoder = null;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            var response = jwtService.checkToken(
                    CheckToken_Request.builder().Token(token).build());

            if (!response.getCheckToken()) {
                throw new JwtException("Token không hợp lệ hoặc đã hết hạn");
            }

            // Kiểm tra trạng thái tài khoản: Chặn ngay lập tức nếu tài khoản bị khóa trong thời gian token còn hạn
            if (userRepository != null) {
                String username = jwtService.extractUsername(token);
                if (username != null) {
                    Optional<User> userOpt = userRepository.findByUsername(username);
                    if (userOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(userOpt.get().getStatus())) {
                        throw new JwtException("Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
                    }
                }
            }
        } catch (JOSEException | ParseException e) {
            throw new JwtException(e.getMessage());
        }

        if (Objects.isNull(nimbusJwtDecoder)) {
            SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(StandardCharsets.UTF_8), "HS512");
            nimbusJwtDecoder = NimbusJwtDecoder
                    .withSecretKey(secretKeySpec)
                    .macAlgorithm(MacAlgorithm.HS512)
                    .build();
        }

        return nimbusJwtDecoder.decode(token);
    }
}
