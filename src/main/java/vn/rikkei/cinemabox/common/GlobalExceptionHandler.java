package vn.rikkei.cinemabox.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.rikkei.cinemabox.booking.InvalidBookingStateException;
import vn.rikkei.cinemabox.booking.NotEnoughSeatsException;
import vn.rikkei.cinemabox.payment.PaymentFailedException;

/**
 * Xử lý lỗi tập trung: chuyển exception thành ApiError.
 * Muốn thêm loại lỗi mới: thêm một phương thức @ExceptionHandler và gọi build(...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu request không hợp lệ", req, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "JSON không hợp lệ hoặc kiểu dữ liệu không đúng", req, List.of());
    }

    @ExceptionHandler(NotEnoughSeatsException.class)
    public ResponseEntity<ApiError> handleNotEnoughSeats(NotEnoughSeatsException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "NOT_ENOUGH_SEATS", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiError> handlePaymentFailed(PaymentFailedException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidBookingStateException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), req, List.of());
    }

    /** Gọi tới đường dẫn không có endpoint nào. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoEndpoint(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không có endpoint " + req.getRequestURI(), req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), req, List.of());
    }

    /** Lưới an toàn cuối cùng: lỗi chưa được xử lý riêng sẽ thành 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Lỗi chưa xử lý tại {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", req, List.of());
    }

    protected ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                             HttpServletRequest req, List<ApiError.FieldViolation> details) {
        ApiError body = ApiError.of(status.value(), error, message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}