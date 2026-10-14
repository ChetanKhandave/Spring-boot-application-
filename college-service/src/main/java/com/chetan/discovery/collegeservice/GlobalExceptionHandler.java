package com.chetan.discovery.collegeservice;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.time.Instant;
import java.util.UUID;

/** Handles controller and Spring MVC errors, retaining HTTP status and protocol headers. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest servlet = ((ServletWebRequest) request).getRequest();
        if (((ServletWebRequest) request).getResponse() != null
                && ((ServletWebRequest) request).getResponse().isCommitted()) {
            return null;
        }
        return respond(exception, status, headers, servlet.getRequestURI());
    }

    /** Unexpected exceptions never expose exception messages, stack traces or payloads. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, request.getRequestURI());
    }

    private ResponseEntity<Object> respond(Exception exception, HttpStatusCode status,
            HttpHeaders originalHeaders, String path) {
        String id = UUID.randomUUID().toString();
        HttpStatus known = HttpStatus.resolve(status.value());
        String title = known == null ? "Request failed" : known.getReasonPhrase();
        String code = status.value() == 400 ? "INVALID_REQUEST" : "HTTP_" + status.value();
        String message = status.value() == 400 ? "Invalid request. Check the parameters and request body."
                : status.is5xxServerError() ? "Unable to process the request. Contact support with the errorId."
                : title;
        if (status.is5xxServerError()) {
            log.error("Request failed errorId={} status={} exceptionType={}", id, status.value(), exception.getClass().getSimpleName());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(new ApiError(Instant.now().toString(), status.value(), title,
                code, message, path, id), headers, status);
    }
}
