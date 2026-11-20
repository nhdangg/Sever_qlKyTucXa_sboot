package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Response.NotificationRecipient_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.NotificationRecipient;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.NotificationRecipient_Mapper;
import vn.QL_KyTucXa.Repository.NotificationRecipient_Repository;

import java.time.LocalDateTime;
import java.util.List;

// Quản lý hộp thư thông báo của từng người dùng
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationRecipient_Service {

    NotificationRecipient_Repository notificationRecipientRepository;
    NotificationRecipient_Mapper recipientMapper;
    CurrentUser_Service currentUserService;

    // Lấy danh sách thông báo trong hộp thư của người dùng đăng nhập có lọc theo
    // trạng thái đã đọc
    @Transactional(readOnly = true)
    public Page_Response<NotificationRecipient_Response> getMyNotifications(int page, int size, Boolean isRead) {
        String userId = currentUserService.userId();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, safeSize, Sort.by("notification.publishedAt").descending());

        Page<NotificationRecipient> trang_thong_bao = (isRead != null)
                ? notificationRecipientRepository.findByUser_UserIdAndIsRead(userId, isRead, pageable)
                : notificationRecipientRepository.findByUser_UserId(userId, pageable);

        List<NotificationRecipient_Response> danh_sach_ket_qua = recipientMapper.toResponseList(trang_thong_bao.getContent());

        return Page_Response.<NotificationRecipient_Response>builder()
                .currentPage(trang_thong_bao.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_thong_bao.getTotalPages())
                .totalElements(trang_thong_bao.getTotalElements())
                .hasPrevious(trang_thong_bao.hasPrevious())
                .hasNext(trang_thong_bao.hasNext())
                .isFirst(trang_thong_bao.isFirst())
                .isLast(trang_thong_bao.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Đánh dấu một thông báo là đã đọc
    @Transactional
    public NotificationRecipient_Response markAsRead(String recipientId) {
        NotificationRecipient nguoi_nhan = notificationRecipientRepository.findById(recipientId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo trong hộp thư"));

        // Kiểm tra quyền sở hữu hộp thư
        String currentUserId = currentUserService.userId();
        if (nguoi_nhan.getUser() == null || !currentUserId.equals(nguoi_nhan.getUser().getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền thao tác trên thông báo này");
        }

        if (!Boolean.TRUE.equals(nguoi_nhan.getIsRead())) {
            nguoi_nhan.setIsRead(true);
            nguoi_nhan.setReadAt(LocalDateTime.now());
            nguoi_nhan = notificationRecipientRepository.save(nguoi_nhan);
        }

        return recipientMapper.toResponse(nguoi_nhan);
    }

    // Đánh dấu tất cả thông báo trong hộp thư của người dùng hiện tại là đã đọc
    @Transactional
    public void markAllAsRead() {
        String userId = currentUserService.userId();
        List<NotificationRecipient> danh_sach_chua_doc = notificationRecipientRepository.findByUser_UserIdAndIsRead(userId, false);
        LocalDateTime now = LocalDateTime.now();
        for (NotificationRecipient nguoi_nhan : danh_sach_chua_doc) {
            nguoi_nhan.setIsRead(true);
            nguoi_nhan.setReadAt(now);
        }
        if (!danh_sach_chua_doc.isEmpty()) {
            notificationRecipientRepository.saveAll(danh_sach_chua_doc);
        }
    }

    // Đếm số lượng thông báo chưa đọc trong hộp thư
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        String userId = currentUserService.userId();
        return notificationRecipientRepository.countByUser_UserIdAndIsRead(userId, false);
    }
}
