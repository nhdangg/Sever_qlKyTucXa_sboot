package vn.QL_KyTucXa.Service;

import org.springframework.stereotype.Component;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;

/**
 * Chính sách kiểm tra trạng thái hoạt động của tài khoản người dùng:
 * Chặn các tài khoản chưa kích hoạt (INACTIVE) hoặc đã bị khóa (BANNED).
 */
@Component
public class AccountStatusPolicy {

    public void assertAccountUsable(User user) {
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
        }
    }
}
