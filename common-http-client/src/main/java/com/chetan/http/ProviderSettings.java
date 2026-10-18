package com.chetan.http;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable settings for one provider. Credentials belong in external secret configuration.
 * @param baseUrl absolute HTTP(S) base URL, without credentials, query or fragment
 * @param connectTimeout positive connection establishment timeout
 * @param readTimeout positive response timeout; not a complete business-operation deadline
 */
public record ProviderSettings(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    /** Validates settings before any requests can be sent. */
    public ProviderSettings {
        Objects.requireNonNull(baseUrl, "baseUrl");
        if (!("https".equalsIgnoreCase(baseUrl.getScheme()) || "http".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URL without credentials, query or fragment");
        }
        positive(connectTimeout, "connectTimeout");
        positive(readTimeout, "readTimeout");
    }

    private static void positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
