package com.adam.restaurantoperations.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.adam.restaurantoperations.auth.service.AuthException;
import com.adam.restaurantoperations.kitchen.KitchenManagementException;
import com.adam.restaurantoperations.inventory.InventoryManagementException;
import com.adam.restaurantoperations.reservations.ReservationManagementException;
import com.adam.restaurantoperations.menu.MenuManagementException;
import com.adam.restaurantoperations.orders.OrderManagementException;
import com.adam.restaurantoperations.tables.TableManagementException;
import com.adam.restaurantoperations.staff.StaffManagementException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(TableManagementException.class)
    ResponseEntity<ApiError> handleTableManagement(
            TableManagementException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ReservationManagementException.class)
    ResponseEntity<ApiError> handleReservationManagement(
            ReservationManagementException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MenuManagementException.class)
    ResponseEntity<ApiError> handleMenuManagement(MenuManagementException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(OrderManagementException.class)
    ResponseEntity<ApiError> handleOrderManagement(OrderManagementException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(KitchenManagementException.class)
    ResponseEntity<ApiError> handleKitchenManagement(
            KitchenManagementException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InventoryManagementException.class)
    ResponseEntity<ApiError> handleInventoryManagement(
            InventoryManagementException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(StaffManagementException.class)
    ResponseEntity<ApiError> handleStaffManagement(
            StaffManagementException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request parameter", request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed or unreadable JSON", request, Map.of());
    }

    @ExceptionHandler({
        PessimisticLockingFailureException.class,
        OptimisticLockingFailureException.class,
        DataIntegrityViolationException.class
    })
    ResponseEntity<ApiError> handlePersistenceContention(Exception exception, HttpServletRequest request) {
        LOGGER.warn("Persistence contention for {}", request.getRequestURI());
        return response(
                HttpStatus.CONFLICT,
                "Request could not be completed; retry",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure for {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors));
    }
}
