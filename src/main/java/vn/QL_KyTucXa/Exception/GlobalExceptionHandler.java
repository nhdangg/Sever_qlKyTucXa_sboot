package vn.QL_KyTucXa.Exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.QL_KyTucXa.DTO.Api_Response;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<Api_Response<Object>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message(exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<Api_Response<Object>> handlingAccessDeniedException(AccessDeniedException exception) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        ErrorCode errorCode = (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                ? ErrorCode.UNAUTHENTICATED
                : ErrorCode.UNAUTHORIZED;

        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AuthenticationException.class)
    public ResponseEntity<Api_Response<Object>> handlingAuthenticationException(AuthenticationException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message(exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Api_Response<Object>> handlingValidation(MethodArgumentNotValidException exception) {
        String enumKey = exception.getFieldError() != null ? exception.getFieldError().getDefaultMessage() : null;
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        // Nếu message truyền vào là một key của ErrorCode (vd "USER_REQUEST")
        if (enumKey != null) {
            try {
                String cleanKey = enumKey.replaceAll("[{}]", "").trim();
                errorCode = ErrorCode.valueOf(cleanKey);
            } catch (IllegalArgumentException ignored) {
            }
        }

        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String msg = error.getDefaultMessage();
            if (msg != null) {
                try {
                    String cleanKey = msg.replaceAll("[{}]", "").trim();
                    ErrorCode ec = ErrorCode.valueOf(cleanKey);
                    msg = ec.getMessage();
                } catch (IllegalArgumentException ignored) {
                }
            }
            errors.put(field, msg);
        });

        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .result(errors)
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = {
            org.springframework.web.multipart.MaxUploadSizeExceededException.class,
            org.springframework.web.multipart.MultipartException.class
    })
    public ResponseEntity<Api_Response<Object>> handlingMaxUploadSizeExceeded(Exception exception) {
        ErrorCode errorCode = ErrorCode.OPERATION_FAILED;
        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message("Dung lượng ảnh/tệp vượt quá giới hạn tối đa cho phép (tối đa 25MB). Vui lòng chọn ảnh có dung lượng nhỏ hơn.")
                .build();
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<Api_Response<Object>> handlingException(Exception exception) {
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        Api_Response<Object> apiResponse = Api_Response.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage() + ": " + exception.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }
}