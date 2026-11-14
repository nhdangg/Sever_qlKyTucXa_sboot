package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountStatusPolicyTest {

    private final AccountStatusPolicy policy = new AccountStatusPolicy();

    @Test
    @DisplayName("Tài khoản ACTIVE hợp lệ không bị ném ngoại lệ")
    void assertAccountUsable_active_doesNotThrow() {
        User user = User.builder().userId("u-1").username("active_user").status("ACTIVE").build();
        assertDoesNotThrow(() -> policy.assertAccountUsable(user));
    }

    @Test
    @DisplayName("Tài khoản null ném USER_NOT_EXISTED")
    void assertAccountUsable_null_throwsUserNotExisted() {
        AppException ex = assertThrows(AppException.class, () -> policy.assertAccountUsable(null));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXISTED);
    }

    @Test
    @DisplayName("Tài khoản INACTIVE ném OPERATION_FAILED")
    void assertAccountUsable_inactive_throwsOperationFailed() {
        User user = User.builder().userId("u-1").username("inactive_user").status("INACTIVE").build();
        AppException ex = assertThrows(AppException.class, () -> policy.assertAccountUsable(user));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tài khoản BANNED ném OPERATION_FAILED")
    void assertAccountUsable_banned_throwsOperationFailed() {
        User user = User.builder().userId("u-1").username("banned_user").status("BANNED").build();
        AppException ex = assertThrows(AppException.class, () -> policy.assertAccountUsable(user));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }
}
