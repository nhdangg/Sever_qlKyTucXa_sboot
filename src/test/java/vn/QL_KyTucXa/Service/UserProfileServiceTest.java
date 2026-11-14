package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private Staff_Repository staffRepository;

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private User_Repository userRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    @DisplayName("Phân giải hồ sơ Sinh viên hiển thị đầy đủ tên và MSSV")
    void resolveUserProfile_student() {
        Role role = Role.builder().roleCode("STUDENT").roleName("Sinh viên").build();
        User user = User.builder().userId("u-stu").username("sv01").role(role).build();
        Student student = Student.builder().fullName("Tran Thi B").studentCode("SV2026001").build();

        when(studentRepository.findByUser_UserId("u-stu")).thenReturn(Optional.of(student));

        UserProfileDTO profile = userProfileService.resolveUserProfile(user);

        assertThat(profile).isNotNull();
        assertThat(profile.getFullName()).isEqualTo("Tran Thi B");
        assertThat(profile.getUserCode()).isEqualTo("SV2026001");
        assertThat(profile.getRoleCode()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("Phân giải hồ sơ Nhân viên hiển thị đầy đủ tên và Mã nhân viên")
    void resolveUserProfile_staff() {
        Role role = Role.builder().roleCode("STAFF").roleName("Nhân viên KTX").build();
        User user = User.builder().userId("u-stf").username("nv01").role(role).build();
        Staff staff = Staff.builder().fullName("Nguyen Van Quan Ly").staffCode("NV001").build();

        when(staffRepository.findByUser_UserId("u-stf")).thenReturn(Optional.of(staff));

        UserProfileDTO profile = userProfileService.resolveUserProfile(user);

        assertThat(profile).isNotNull();
        assertThat(profile.getFullName()).isEqualTo("Nguyen Van Quan Ly");
        assertThat(profile.getUserCode()).isEqualTo("NV001");
        assertThat(profile.getRoleCode()).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("Phân giải hồ sơ Quản trị viên (ADMIN)")
    void resolveUserProfile_admin() {
        Role role = Role.builder().roleCode("ADMIN").roleName("Quản trị viên").build();
        User user = User.builder().userId("u-adm").username("admin").role(role).build();

        UserProfileDTO profile = userProfileService.resolveUserProfile(user);

        assertThat(profile).isNotNull();
        assertThat(profile.getFullName()).isEqualTo("Quản trị viên");
        assertThat(profile.getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("resolveUserProfileByUserId tìm thấy người dùng và trả về UserProfileDTO")
    void resolveUserProfileByUserId_success() {
        Role role = Role.builder().roleCode("ADMIN").roleName("Quản trị viên").build();
        User user = User.builder().userId("u-adm-01").username("admin01").role(role).build();

        when(userRepository.findById("u-adm-01")).thenReturn(Optional.of(user));

        UserProfileDTO profile = userProfileService.resolveUserProfileByUserId("u-adm-01");

        assertThat(profile).isNotNull();
        assertThat(profile.getFullName()).isEqualTo("Quản trị viên");
        assertThat(profile.getRoleCode()).isEqualTo("ADMIN");
    }
}
