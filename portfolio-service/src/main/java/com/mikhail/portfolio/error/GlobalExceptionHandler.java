package com.mikhail.portfolio.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<ApiError> handlePortfolioNotFound(PortfolioNotFoundException e,
                                                            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request, null);
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ApiError> handlePositionNotFound(PositionNotFoundException e,
                                                           HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request, null);
    }

    @ExceptionHandler(PositionAlreadyExistsException.class)
    public ResponseEntity<ApiError> handlePositionAlreadyExists(PositionAlreadyExistsException e,
                                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e,
                                                     HttpServletRequest request) {
        List<ApiError.FieldErrorDto> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldErrorDto(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null ? "некорректное значение" : fieldError.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Запрос не прошёл валидацию", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e,
                                                         HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Некорректное тело запроса", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Параметр '" + e.getName() + "' имеет неверный формат", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           List<ApiError.FieldErrorDto> fieldErrors) {
        ApiError body = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
