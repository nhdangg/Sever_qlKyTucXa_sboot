package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Feedback_Request;
import vn.QL_KyTucXa.DTO.Response.Feedback_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Feedback_Service;

import java.util.List;

@RestController
@RequestMapping({ "/api/feedbacks", "/api/feedback" })
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Feedback_Controller {

    Feedback_Service feedbackService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('FEEDBACK_CREATE')")
    public Api_Response<Feedback_Response> submitFeedback(@RequestBody @Valid Feedback_Request request) {
        return Api_Response.<Feedback_Response>builder()
                .result(feedbackService.create(request))
                .build();
    }

    @GetMapping({ "/my", "/me" })
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Feedback_Response>> getMyFeedbacks() {
        return Api_Response.<List<Feedback_Response>>builder()
                .result(feedbackService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{feedbackId}")
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    public Api_Response<Feedback_Response> getFeedbackById(@PathVariable String feedbackId) {
        return Api_Response.<Feedback_Response>builder()
                .result(feedbackService.getById(feedbackId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    public Api_Response<List<Feedback_Response>> getFeedbacksByStudent(@PathVariable String studentId) {
        return Api_Response.<List<Feedback_Response>>builder()
                .result(feedbackService.getByStudentId(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    public Api_Response<Page_Response<Feedback_Response>> getAllFeedbacks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return Api_Response.<Page_Response<Feedback_Response>>builder()
                .result(feedbackService.getAll(page, size, keyword, status, category, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{feedbackId}/respond")
    @PreAuthorize("hasAuthority('FEEDBACK_MANAGE')")
    public Api_Response<Feedback_Response> respondToFeedback(
            @PathVariable String feedbackId,
            @RequestParam(required = false) String staffId,
            @RequestParam String response) {
        return Api_Response.<Feedback_Response>builder()
                .result(feedbackService.respond(feedbackId, staffId, response))
                .build();
    }

    @DeleteMapping("/{feedbackId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Api_Response<String> deleteFeedback(@PathVariable String feedbackId) {
        feedbackService.delete(feedbackId);
        return Api_Response.<String>builder()
                .result(feedbackId)
                .build();
    }
}
