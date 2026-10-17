# Common HTTP client

Reusable, synchronous outbound HTTP library for the MVC services. Compatible with the repository's Java 17 baseline and Java 21. It is a plain JAR, not a separately deployed service. Student, employee and college services declare it as a dependency; no external requests run at startup.

## Design

* **Single responsibility:** settings validate configuration; factory creates provider instances; adapter executes HTTP. Business DTOs, endpoints and response interpretation remain in each service.
* **Dependency inversion / interface segregation:** provider adapters depend on the small `ApiClient` interface.
* **Open/Closed:** customize a provider's builder without editing the executor. Builder customization and interceptors supply authentication and error-handling strategies.
* **Factory and Adapter:** `ApiClientFactory` creates isolated instances; `RestApiClient` adapts Spring's client. Composition keeps provider-specific behavior separate.

Do not add abstractions merely to name a design pattern. Share instances after construction; interceptors, token suppliers and serializers must also be thread-safe.

## Register two providers explicitly

Adding a dependency does not register beans automatically. Put this configuration inside your service's component scan. Spring Boot binds the properties through `@Value`; secrets come from environment variables or a secret manager.

```java
import com.chetan.http.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalProvidersConfiguration {
    @Bean("usersApi")
    ApiClient usersApi(RestClient.Builder builder,
            @Value("${providers.users.base-url}") URI url,
            @Value("${providers.users.connect-timeout:3s}") Duration connect,
            @Value("${providers.users.read-timeout:10s}") Duration read) {
        return ApiClientFactory.create(builder,
                new ProviderSettings(url, connect, read), b -> {});
    }

    @Bean("paymentsApi")
    ApiClient paymentsApi(RestClient.Builder builder,
            @Value("${providers.payments.base-url}") URI url,
            @Value("${providers.payments.connect-timeout:2s}") Duration connect,
            @Value("${providers.payments.read-timeout:5s}") Duration read) {
        return ApiClientFactory.create(builder,
                new ProviderSettings(url, connect, read), b -> {});
    }
}
```

```properties
providers.users.base-url=${USERS_API_BASE_URL}
providers.users.connect-timeout=3s
providers.users.read-timeout=10s
providers.payments.base-url=${PAYMENTS_API_BASE_URL}
providers.payments.connect-timeout=2s
providers.payments.read-timeout=5s
```

Inject `@Qualifier("usersApi") ApiClient client` into your provider adapter using constructor injection. Reuse beans rather than creating a client for every call.

```java
UserResponse user = client.get("/users/{id}", UserResponse.class, 101);
List<UserResponse> users = client.get("/users",
        new ParameterizedTypeReference<List<UserResponse>>() {});
UserResponse created = client.post("/users", request, UserResponse.class);
ResponseEntity<ApiResponse<UserResponse>> response = client.exchange(
        HttpMethod.POST, "/users", request,
        headers -> headers.setBearerAuth(token),
        new ParameterizedTypeReference<ApiResponse<UserResponse>>() {});
```

DTO names above are illustrative, defined in your consuming service. Import `java.util.List`, Spring's `ParameterizedTypeReference`, `HttpMethod` and `ResponseEntity` as needed. Use `exchange` for PUT/PATCH/DELETE, parameterized POST responses, or status/headers. Use `Void` for no response body. A successful empty response produces null from `get`/`post`; `exchange` still returns status/headers. Request bodies are JSON; multipart/streaming require a separate adapter.

## Authentication, failures and operational behavior

* For rotating tokens, configure a provider-specific `requestInterceptor` that obtains a token per request, or supply per-request headers through `exchange`. Avoid caching an expiring token in a default header.
* Set static headers using the factory customizer's `defaultHeader`. Never log credentials, request/response bodies or raw provider error bodies.
* Spring's `RestClientResponseException` subclasses represent 4xx/5xx failures, `ResourceAccessException` represents transport/timeouts, and `RestClientException` covers conversion failures. Provider adapters should translate these into their own domain exceptions. The library does not install a global controller advice or expose provider failures directly to API consumers. A provider can override status handling via `defaultStatusHandler` in its customizer.
* No automatic retries: decide per operation, considering idempotency, transient failure categories and a bounded retry budget. POST retries require a provider guarantee such as idempotency keys.
* Redirects are disabled; 3xx status/headers remain available through `exchange`. If redirects are needed, handle them deliberately and validate destination hosts before forwarding credentials.
* Only provider-relative paths beginning with one `/` are accepted. URI templates encode variables; do not concatenate untrusted text into paths. Provider base URLs are trusted deployment configuration, not user input.
* Connection and response timeouts are per provider. They are not an end-to-end operation deadline. A custom transport may be supplied through the builder customizer when different TLS, pooling or proxy requirements arise; that deliberately replaces factory transport settings.
* Cloning Spring Boot's builder retains its observability configuration. Each service process owns its own instances and connections.

## Validation

```bash
mvn -pl common-http-client -am test
mvn -pl common-http-client -am javadoc:javadoc
mvn clean verify
```

JUnit tests cover concrete/nested generic conversion, JSON requests, empty responses, status and headers, dynamic authentication, HTTP failures without retries, transport failures, malformed JSON, invalid paths/settings/null inputs, and provider isolation. Full application smoke tests require the repository's documented runtime dependencies. Tests cannot establish compatibility with an unseen provider contract; add contract tests in each service when integrating actual providers.
