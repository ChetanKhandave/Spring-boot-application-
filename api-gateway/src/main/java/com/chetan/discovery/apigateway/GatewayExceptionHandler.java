package com.chetan.discovery.apigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** Handles reactive routing/filter failures; downstream HTTP error responses pass through untouched. */
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final ObjectMapper mapper;
    public GatewayExceptionHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) { return Mono.error(exception); }
        HttpStatusCode status = exception instanceof ErrorResponse error
                ? error.getStatusCode() : transportStatus(exception);
        HttpStatus known = HttpStatus.resolve(status.value());
        String title = known == null ? "Request failed" : known.getReasonPhrase();
        String id = UUID.randomUUID().toString();
        String message = status.is5xxServerError()
                ? "Unable to process the request. Contact support with the errorId." : title;
        ApiError body = new ApiError(Instant.now().toString(), status.value(), title,
                status.value() == 400 ? "INVALID_REQUEST" : "HTTP_" + status.value(), message,
                exchange.getRequest().getURI().getRawPath(), id);
        final byte[] bytes;
        try { bytes = mapper.writeValueAsBytes(body); }
        catch (Exception serializationFailure) { return Mono.error(serializationFailure); }
        if (exception instanceof ErrorResponse error) { exchange.getResponse().getHeaders().putAll(error.getHeaders()); }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status.is5xxServerError()) {
            log.error("Gateway failure errorId={} status={} exceptionType={}", id, status.value(), exception.getClass().getSimpleName());
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private HttpStatus transportStatus(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof io.netty.handler.timeout.ReadTimeoutException) { return HttpStatus.GATEWAY_TIMEOUT; }
            if (cause instanceof ConnectException) { return HttpStatus.SERVICE_UNAVAILABLE; }
            if (cause.getCause() == cause) { break; }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
