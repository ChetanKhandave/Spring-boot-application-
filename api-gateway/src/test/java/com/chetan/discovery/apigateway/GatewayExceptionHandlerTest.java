package com.chetan.discovery.apigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies gateway routing/transport errors and response commitment behavior. */
class GatewayExceptionHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayExceptionHandler handler = new GatewayExceptionHandler(mapper);
    private void verify(Throwable failure, int expected) throws Exception {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/students/101?token=secret").build());
        handler.handle(exchange, failure).block();
        assertEquals(expected, exchange.getResponse().getStatusCode().value());
        String text = exchange.getResponse().getBodyAsString().block();
        var body = mapper.readTree(text);
        assertEquals(expected, body.path("status").asInt());
        assertEquals("/students/101", body.path("path").asText());
        assertFalse(text.contains("secret"));
        assertTrue(body.path("errorId").asText().matches("[a-f0-9-]{36}"));
    }
    @Test void unknownRouteReturns404() throws Exception { verify(new ResponseStatusException(HttpStatus.NOT_FOUND, "secret"), 404); }
    @Test void unavailableServiceReturns503() throws Exception { verify(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "secret"), 503); }
    @Test void connectionFailureReturns503() throws Exception { verify(new RuntimeException(new ConnectException("secret")), 503); }
    @Test void timeoutReturns504() throws Exception { verify(new TimeoutException("secret"), 504); }
    @Test void unexpectedFailureReturns500() throws Exception { verify(new IllegalStateException("secret"), 500); }
    @Test void committedResponseIsNotRewritten() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setComplete().block();
        var failure = new IllegalStateException("already committed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> handler.handle(exchange, failure).block()));
    }
}
