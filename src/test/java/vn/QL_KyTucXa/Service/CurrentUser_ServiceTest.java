package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUser_ServiceTest {

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private Staff_Repository staffRepository;

    @Mock
    private User_Repository userRepository;

    @InjectMocks
    private CurrentUser_Service currentUserService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockJwtAuthentication(String userId, String username) {
        Jwt jwt = new Jwt(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("userId", userId, "sub", username)
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"), new SimpleGrantedAuthority("STUDENT_MANAGE"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Lấy userId thành công từ JWT claim")
    void userId_success_fromJwtClaim() {
        mockJwtAuthentication("user-123", "testuser");

        String userId = currentUserService.userId();

        assertThat(userId).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Lấy userId thất bại khi chưa xác thực")
    void userId_failed_whenUnauthenticated() {
        AppException ex = assertThrows(AppException.class, () -> currentUserService.userId());
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Lấy studentId thành công khi người dùng có hồ sơ sinh viên")
    void studentId_success() {
        mockJwtAuthentication("user-123", "testuser");
        Student student = Student.builder().studentId("stu-01").build();
        when(studentRepository.findByUser_UserId("user-123")).thenReturn(Optional.of(student));

        String studentId = currentUserService.studentId();

        assertThat(studentId).isEqualTo("stu-01");
    }

    @Test
    @DisplayName("Lấy studentId ném STUDENT_NOT_FOUND khi không có hồ sơ và không fallback sinh viên khác")
    void studentId_failed_whenNotFound_noFallback() {
        mockJwtAuthentication("user-123", "testuser");
        when(studentRepository.findByUser_UserId("user-123")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> currentUserService.studentId());
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        verify(studentRepository, never()).findAll();
    }

    @Test
    @DisplayName("findStudentId trả về Optional chứa ID khi tìm thấy hồ sơ")
    void findStudentId_success() {
        mockJwtAuthentication("user-123", "testuser");
        Student student = Student.builder().studentId("stu-01").build();
        when(studentRepository.findByUser_UserId("user-123")).thenReturn(Optional.of(student));

        Optional<String> studentIdOpt = currentUserService.findStudentId();

        assertThat(studentIdOpt).isPresent().contains("stu-01");
    }

    @Test
    @DisplayName("findStudentId trả về Optional.empty khi không tìm thấy hồ sơ")
    void findStudentId_empty_whenNotFound() {
        mockJwtAuthentication("user-123", "testuser");
        when(studentRepository.findByUser_UserId("user-123")).thenReturn(Optional.empty());

        Optional<String> studentIdOpt = currentUserService.findStudentId();

        assertThat(studentIdOpt).isEmpty();
    }

    @Test
    @DisplayName("Lấy staffId thành công khi người dùng có hồ sơ nhân viên")
    void staffId_success() {
        mockJwtAuthentication("user-123", "testuser");
        Staff staff = Staff.builder().staffId("staff-01").build();
        when(staffRepository.findByUser_UserId("user-123")).thenReturn(Optional.of(staff));

        String staffId = currentUserService.staffId();

        assertThat(staffId).isEqualTo("staff-01");
    }

    @Test
    @DisplayName("Lấy staffId ném STAFF_NOT_FOUND khi không có hồ sơ và không fallback nhân viên khác")
    void staffId_failed_whenNotFound_noFallback() {
        mockJwtAuthentication("user-123", "testuser");
        when(staffRepository.findByUser_UserId("user-123")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> currentUserService.staffId());
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STAFF_NOT_FOUND);
        verify(staffRepository, never()).findAll();
    }

    @Test
    @DisplayName("findStaffId trả về Optional chứa ID khi tìm thấy hồ sơ nhân viên")
    void findStaffId_success() {
        mockJwtAuthentication("user-123", "testuser");
        Staff staff = Staff.builder().staffId("staff-01").build();
        when(staffRepository.findByUser_UserId("user-123")).thenReturn(Optional.of(staff));

        Optional<String> staffIdOpt = currentUserService.findStaffId();

        assertThat(staffIdOpt).isPresent().contains("staff-01");
    }

    @Test
    @DisplayName("findStaffId trả về Optional.empty khi tài khoản admin không có hồ sơ nhân viên")
    void findStaffId_empty_whenAdminWithoutStaffProfile() {
        mockJwtAuthentication("admin-id", "admin");
        when(staffRepository.findByUser_UserId("admin-id")).thenReturn(Optional.empty());

        Optional<String> staffIdOpt = currentUserService.findStaffId();

        assertThat(staffIdOpt).isEmpty();
        verify(staffRepository, never()).findAll();
    }

    @Test
    @DisplayName("hasRole trả về true khi người dùng có vai trò tương ứng")
    void hasRole_true() {
        mockJwtAuthentication("user-123", "testuser");

        assertThat(currentUserService.hasRole("STAFF")).isTrue();
        assertThat(currentUserService.hasRole("STUDENT")).isFalse();
    }

    @Test
    @DisplayName("hasPermission trả về true khi người dùng có quyền cụ thể")
    void hasPermission_true() {
        mockJwtAuthentication("user-123", "testuser");

        assertThat(currentUserService.hasPermission("STUDENT_MANAGE")).isTrue();
        assertThat(currentUserService.hasPermission("CONTRACT_MANAGE")).isFalse();
    }

    @Test
    @DisplayName("hasPermission tự động cấp quyền cho người dùng có ROLE_ADMIN")
    void hasPermission_trueForAdmin() {
        Jwt jwt = new Jwt(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("userId", "admin-123", "sub", "admin")
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        assertThat(currentUserService.hasPermission("STUDENT_MANAGE")).isTrue();
        assertThat(currentUserService.hasPermission("ANY_OTHER_PERMISSION")).isTrue();
    }
}

