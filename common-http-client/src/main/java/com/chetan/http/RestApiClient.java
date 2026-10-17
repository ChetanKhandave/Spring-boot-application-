package com.chetan.http;

import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/** RestClient adapter. Immutable after construction; reuse across concurrent callers.
 * Spring HTTP status, transport and conversion exceptions propagate to provider adapters.
 * This class neither logs payloads nor retries potentially state-changing requests.
 */
public final class RestApiClient implements ApiClient {
    private final RestClient client;

    /** Wrap a fully configured client. Configuration must be completed before publication. */
    public RestApiClient(RestClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public <T> T get(String path, Class<T> type, Object... variables) {
        return get(path, ParameterizedTypeReference.forType(Objects.requireNonNull(type, "responseType")), variables);
    }

    @Override
    public <T> T get(String path, ParameterizedTypeReference<T> type, Object... variables) {
        return exchange(HttpMethod.GET, path, null, h -> {}, type, variables).getBody();
    }

    @Override
    public <T> T post(String path, Object body, Class<T> type, Object... variables) {
        Objects.requireNonNull(body, "body");
        return exchange(HttpMethod.POST, path, body, h -> {},
                ParameterizedTypeReference.<T>forType(Objects.requireNonNull(type, "responseType")), variables).getBody();
    }

    @Override
    public <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body,
            Consumer<HttpHeaders> headers, ParameterizedTypeReference<T> type, Object... variables) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(type, "responseType");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(variables, "variables");
        validatePath(path);
        var request = client.method(method).uri(path, variables).headers(headers);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(type);
    }

    private static void validatePath(String path) {
        Objects.requireNonNull(path, "path");
        // Keep requests bound to the configured provider; never accept a caller-supplied host.
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("path must start with one slash and contain no backslashes or control characters");
        }
    }
}
