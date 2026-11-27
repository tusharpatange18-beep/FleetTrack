package com.fleettrack.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps every exception to the same JSON error body ({@link ApiError}).
 * Client mistakes get a 4xx with a useful message; only real server faults reach the 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 404 ----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    // ---- 400 ----

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** A @Valid request body broke a rule, e.g. @NotNull. Lists every bad field, sorted. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> problems = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // e.g. "latitude: must not be null"
            problems.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, joinSorted(problems), request);
    }

    /** Thrown for @RequestParam / @PathVariable constraints on a @Validated controller. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> problems = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            // The path looks like "getNearbyVehicles.radiusKm"; keep only "radiusKm"
            String paramName = lastNode(violation.getPropertyPath().toString());
            problems.add(paramName + ": " + violation.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, joinSorted(problems), request);
    }

    /** Spring's built-in method validation (used when the controller is not @Validated). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> problems = new ArrayList<>();
        for (MessageSourceResolvable error : ex.getAllErrors()) {
            problems.add(error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, joinSorted(problems), request);
    }

    /** The JSON body could not be read: broken JSON, or a value of the wrong type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Only a wrong value (not broken JSON) lets us name the field
        if (!(ex.getCause() instanceof InvalidFormatException)) {
            return build(HttpStatus.BAD_REQUEST, "Malformed JSON body", request);
        }
        InvalidFormatException formatError = (InvalidFormatException) ex.getCause();
        if (formatError.getPath().isEmpty()) {
            return build(HttpStatus.BAD_REQUEST, "Malformed JSON body", request);
        }

        String field = fieldName(formatError);
        String message = field + ": invalid value '" + formatError.getValue() + "'";

        // For an enum (e.g. status), also list the values that are allowed
        Class<?> target = formatError.getTargetType();
        if (target != null && target.isEnum()) {
            List<String> allowed = new ArrayList<>();
            for (Object constant : target.getEnumConstants()) {
                allowed.add(constant.toString());
            }
            message = message + "; allowed values: " + String.join(", ", allowed);
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Builds the field's full name from its JSON path, e.g. "items.[2].status".
     * Each step is a field name, or a list position written as [index].
     */
    private static String fieldName(InvalidFormatException formatError) {
        List<String> parts = new ArrayList<>();
        for (JsonMappingException.Reference ref : formatError.getPath()) {
            if (ref.getFieldName() != null) {
                parts.add(ref.getFieldName());
            } else {
                parts.add("[" + ref.getIndex() + "]");
            }
        }
        return String.join(".", parts);
    }

    /** Sorts the messages alphabetically and joins them with "; ". */
    private static String joinSorted(List<String> messages) {
        Collections.sort(messages);
        return String.join("; ", messages);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "': " + ex.getValue(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", request);
    }

    // ---- 401 / 403 / 405 ----

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Use the exception's own message if it has one
        String message = "Access denied";
        if (ex.getMessage() != null) {
            message = ex.getMessage();
        }
        return build(HttpStatus.FORBIDDEN, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    // ---- 409 ----

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** A unique or foreign-key constraint fired, usually from a race the service-level check could not see. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data (duplicate or invalid reference)", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleLocking(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The resource was modified concurrently; please retry", request);
    }

    // ---- 500 ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    /** Every handler above ends here: HTTP status + our standard JSON error body. */
    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(status, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** "a.b.c" becomes "c". Text without a dot is returned unchanged. */
    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        if (dot < 0) {
            return propertyPath;
        }
        return propertyPath.substring(dot + 1);
    }
}
