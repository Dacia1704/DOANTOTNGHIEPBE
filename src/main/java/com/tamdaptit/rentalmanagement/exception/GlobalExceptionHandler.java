package com.tamdaptit.rentalmanagement.exception;

import com.tamdaptit.rentalmanagement.common.ApiResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bat cac loi nghiep vu chu dong nem ra (AppException)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(errorCode.getCode().value())
                        .message(ex.getMessage())
                        .build();

        return ResponseEntity.status(errorCode.getCode()).body(response);
    }

    // 1. Loi Validation (Khi dung @Valid cho DTO nhung client gui sai format)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        if (errorMessage.isBlank()) {
            errorMessage = "Du lieu gui len khong hop le";
        }

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .message(errorMessage)
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 2. Sai kieu du lieu trong path variable / query param
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .message("Tham so '" + ex.getName() + "' khong hop le")
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 3. Khong co quyen truy cap (thi du @PreAuthorize that bai)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.FORBIDDEN.value())
                        .message(ErrorCode.UNAUTHORIZED.getMessage())
                        .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // 4. Loi xac thuc cua Spring Security
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(
            AuthenticationException ex) {

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.UNAUTHORIZED.value())
                        .message(ErrorCode.UNAUTHENTICATED.getMessage())
                        .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // 5. Vi pham rang buoc du lieu o tang DB (trung email, trung slug, FK khong ton tai...)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        log.warn("Data integrity violation", ex);

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .message("Du lieu bi trung hoac khong hop le")
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 6. Loi Runtime (Loi logic nghiep vu tu nem ra)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception", ex);

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .message(ex.getMessage())
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 7. Loi trang thai HTTP khong xu ly duoc
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(
            NoResourceFoundException ex) {

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.NOT_FOUND.value())
                        .message(ErrorCode.NOT_FOUND.getMessage())
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 8. Bat toan bo cac loi he thong khong luong truoc duoc (tranh sap app)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);

        ApiResponse<Object> response =
                ApiResponse.builder()
                        .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                        .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}