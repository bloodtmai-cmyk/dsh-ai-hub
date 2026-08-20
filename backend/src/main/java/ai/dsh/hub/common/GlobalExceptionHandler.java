package ai.dsh.hub.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Problem> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new Problem(exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<Problem> handleValidation(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数无效")
                : exception.getMessage();
        return ResponseEntity.badRequest().body(new Problem("VALIDATION_FAILED", message, Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> handleDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Problem("ACCESS_DENIED", "无权执行此操作", Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Problem> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new Problem("UPLOAD_TOO_LARGE", "上传文件超过服务器允许的大小", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Problem("INTERNAL_ERROR", "服务处理失败", Instant.now()));
    }

    public record Problem(String code, String message, Instant timestamp) {
    }
}
