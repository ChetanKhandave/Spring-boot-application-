package com.chetan.http;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Factory for independently configured provider clients.
 * Pass Spring Boot's injected builder to retain its converters and observability customizations.
 * Builder cloning prevents provider credentials, headers and policies from leaking to other clients.
 */
public final class ApiClientFactory {
    private ApiClientFactory() {}

    /** Creates a client with provider-specific builder customization (Strategy).
     * Configure interceptors for dynamic tokens, default headers, or defaultStatusHandler here.
     * Customizers must not replace the provider URL or transport unless intentionally overriding settings.
     * Redirects are disabled so credentials cannot follow a redirect to another host.
     * @param prototype preconfigured builder; never modified by this method
     * @param settings provider URL and timeouts
     * @param customize provider-specific extension; use b -&gt; {} when unnecessary
     * @return an independent reusable client
     */
    public static ApiClient create(RestClient.Builder prototype, ProviderSettings settings,
            Consumer<RestClient.Builder> customize) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(customize, "customize");
        var http = HttpClient.newBuilder().connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var transport = new JdkClientHttpRequestFactory(http);
        transport.setReadTimeout(settings.readTimeout());
        var builder = prototype.clone().baseUrl(settings.baseUrl().toString())
                .requestFactory(transport).defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        customize.accept(builder);
        return new RestApiClient(builder.build());
    }
}
