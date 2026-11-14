package vn.QL_KyTucXa.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.Exception.ErrorCode;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

        private static final String[] PUBLIC_ENDPOINTS = {
                        // API
                        "/api/auth/**",
                        "/api/vietqr/webhook",
                        "/api/vietqr/**",
                        "/api/payments/webhook",
                        "/api/payments/sepay-webhook",
                        // UI_UX
                        "/", "/login", "/dashboard", "/student-portal", "/buildings", "/rooms", "/students",
                        "/contracts", "/invoices", "/maintenance", "/settings", "/admin/**",
                        // STATIC_RESOURCES (nhóm tài nguyê tĩnh)
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico", "/error"
        };
        private static final String[] ADMIN_POST_ENDPOINTS = {
                        "/api/users/**", "/api/roles/**", "/api/permissions/**", "/api/staffs/**", "/api/settings/**"
        };
        private static final String[] STAFF_ADMIN_POST_ENDPOINTS = {
                        "/api/students/**", "/api/buildings/**", "/api/floors/**", "/api/rooms/**",
                        "/api/room-types/**", "/api/beds/**", "/api/contracts/**", "/api/invoices/**",
                        "/api/utility-readings/**", "/api/assets/**", "/api/room-assets/**", "/api/service-types/**",
                        "/api/registration-periods/**", "/api/violations/**", "/api/violation-types/**",
                        "/api/notifications/**"
        };
        private static final String[] STUDENT_ALLOWED_POST_ENDPOINTS = {
                        "/api/contracts/apply", // Nộp đơn đăng ký hợp đồng nội trú trực tuyến
                        "/api/students/upload-avatar", // Lưu ảnh đại diện sinh viên (dành cho sinh viên tự đổi avatar)
                        "/api/maintenance-requests/**", // Gửi yêu cầu bảo trì, sửa chữa
                        "/api/feedbacks/**", // Gửi ý kiến, đánh giá
                        "/api/feedback/**", // Tương thích đường dẫn cũ
                        "/api/room-transfers/**", // Gửi đơn xin đổi phòng
                        "/api/vehicle-registrations/**", // Đăng ký xe
                        "/api/visitors/**", // Đăng ký khách đến thăm
                        "/api/payments/**" // Thực hiện thanh toán hóa đơn
        };

        @Autowired
        private CustomJwtDecoder customJwtDecoder;

        @Bean
        public AuthenticationEntryPoint authenticationEntryPoint() {
                return (request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");

                        Api_Response<Object> apiResponse = Api_Response.builder()
                                        .code(ErrorCode.UNAUTHENTICATED.getCode())
                                        .message(ErrorCode.UNAUTHENTICATED.getMessage())
                                        .build();

                        new ObjectMapper().writeValue(response.getOutputStream(), apiResponse);
                };
        }

        @Bean
        public AccessDeniedHandler accessDeniedHandler() {
                return (request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");

                        Api_Response<Object> apiResponse = Api_Response.builder()
                                        .code(ErrorCode.UNAUTHORIZED.getCode())
                                        .message(ErrorCode.UNAUTHORIZED.getMessage())
                                        .build();

                        new ObjectMapper().writeValue(response.getOutputStream(), apiResponse);
                };
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)

                                .cors(Customizer.withDefaults())

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwtConfigurer -> jwtConfigurer
                                                                .decoder(customJwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter()))
                                                .authenticationEntryPoint(authenticationEntryPoint())
                                                .accessDeniedHandler(accessDeniedHandler()))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                                                .requestMatchers(HttpMethod.POST, ADMIN_POST_ENDPOINTS)
                                                .hasAnyRole("ADMIN", "MANAGER")
                                                .requestMatchers(HttpMethod.POST, STUDENT_ALLOWED_POST_ENDPOINTS)
                                                .hasAnyRole("ADMIN", "MANAGER", "STAFF", "STUDENT")
                                                .requestMatchers(HttpMethod.POST, STAFF_ADMIN_POST_ENDPOINTS)
                                                .hasAnyRole("ADMIN", "MANAGER", "STAFF")

                                                .anyRequest().authenticated());

                return http.build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

                jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

                JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
                return jwtAuthenticationConverter;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(10);
        }
}
