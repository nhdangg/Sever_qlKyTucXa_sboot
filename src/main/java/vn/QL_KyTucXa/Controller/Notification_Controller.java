package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Notification_Request;
import vn.QL_KyTucXa.DTO.Response.Notification_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.Notification_Service;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Notification_Controller {

    Notification_Service notificationService;

    @PostMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Api_Response<Notification_Response> createNotification(@RequestBody @Valid Notification_Request request) {
        return Api_Response.<Notification_Response>builder()
                .result(notificationService.create(request))
                .build();
    }

    @GetMapping("/{notificationId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Notification_Response> getNotificationById(@PathVariable String notificationId) {
        return Api_Response.<Notification_Response>builder()
                .result(notificationService.getById(notificationId))
                .build();
    }

    @GetMapping("/published")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Page_Response<Notification_Response>> getPublishedNotifications(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "publishedAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Notification_Response>>builder()
                .result(notificationService.getPublishedNotifications(page, size, sortBy, sortDir))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Api_Response<Page_Response<Notification_Response>> getAllNotifications(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "isPublished", required = false) Boolean isPublished,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Notification_Response>>builder()
                .result(notificationService.getAll(page, size, title, isPublished, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{notificationId}")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Api_Response<Notification_Response> updateNotification(
            @PathVariable String notificationId,
            @RequestBody @Valid Notification_Request request
    ) {
        return Api_Response.<Notification_Response>builder()
                .result(notificationService.update(notificationId, request))
                .build();
    }

    @PutMapping("/{notificationId}/publish")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Api_Response<Notification_Response> publishNotification(@PathVariable String notificationId) {
        return Api_Response.<Notification_Response>builder()
                .result(notificationService.publish(notificationId))
                .build();
    }

    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Api_Response<String> deleteNotification(@PathVariable String notificationId) {
        notificationService.delete(notificationId);
        return Api_Response.<String>builder()
                .result(notificationId)
                .build();
    }
}
