package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class RoleBasedTwoFactorPolicyTest {

    private RoleBasedTwoFactorPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RoleBasedTwoFactorPolicy();
    }

    @Test
    @DisplayName("Tài khoản username là admin phải yêu cầu 2FA")
    void requires2FA_usernameAdmin() {
        User user = User.builder().username("admin").build();
        assertThat(policy.requiresTwoFactorAuth(user)).isTrue();
    }

    @Test
    @DisplayName("Tài khoản có vai trò ADMIN phải yêu cầu 2FA")
    void requires2FA_roleAdmin() {
        Role adminRole = Role.builder().roleCode("ADMIN").build();
        User user = User.builder().username("manager").role(adminRole).build();
        assertThat(policy.requiresTwoFactorAuth(user)).isTrue();
    }

    @Test
    @DisplayName("Tài khoản Sinh viên hoặc Nhân viên thông thường không yêu cầu 2FA")
    void requires2FA_studentOrStaff() {
        Role studentRole = Role.builder().roleCode("STUDENT").build();
        User student = User.builder().username("sv01").role(studentRole).build();
        assertThat(policy.requiresTwoFactorAuth(student)).isFalse();

        Role staffRole = Role.builder().roleCode("STAFF").build();
        User staff = User.builder().username("nv01").role(staffRole).build();
        assertThat(policy.requiresTwoFactorAuth(staff)).isFalse();
    }

    @Test
    @DisplayName("User null trả về false")
    void requires2FA_nullUser() {
        assertThat(policy.requiresTwoFactorAuth(null)).isFalse();
    }
}
