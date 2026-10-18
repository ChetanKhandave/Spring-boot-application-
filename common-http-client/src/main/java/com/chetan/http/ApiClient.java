package com.chetan.http;

import java.util.function.Consumer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Small transport abstraction. Provider adapters own endpoint paths and business DTOs. */
public interface ApiClient {
    /** GET a concrete response type. Empty successful bodies return null. */
    <T> T get(String path, Class<T> responseType, Object... variables);

    /** GET a parameterized response, preserving nested generic type information. */
    <T> T get(String path, ParameterizedTypeReference<T> responseType, Object... variables);

    /** POST JSON; no automatic retries are performed. */
    <T> T post(String path, Object body, Class<T> responseType, Object... variables);

    /** Execute an operation with per-request headers and access to status and response headers.
     * Use Void as response type for bodyless responses and null for no request body.
     * Header consumers must not retain the mutable headers or use shared mutable state.
     */
    <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body,
            Consumer<HttpHeaders> headers, ParameterizedTypeReference<T> responseType,
            Object... variables);
}
