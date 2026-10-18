package com.chetan.http;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Contract tests exercise real request construction and Jackson conversion without external APIs. */
class ApiClientTest {
    private MockRestServiceServer server;
    private ApiClient client;
    record User(long id, String name) {}
    record Envelope<T>(T data) {}

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://provider.example");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestApiClient(builder.build());
    }
    @AfterEach void verify() { server.verify(); }

    @Test void concreteResponseAndUriVariable() {
        server.expect(requestTo("https://provider.example/users/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":42,\"name\":\"Chetan\"}", MediaType.APPLICATION_JSON));
        assertThat(client.get("/users/{id}", User.class, 42)).isEqualTo(new User(42,"Chetan"));
    }

    @Test void nestedGenericsRetainElementTypes() {
        server.expect(requestTo("https://provider.example/users"))
                .andRespond(withSuccess("{\"data\":[{\"id\":1,\"name\":\"A\"}]}", MediaType.APPLICATION_JSON));
        Envelope<List<User>> result = client.get("/users", new ParameterizedTypeReference<Envelope<List<User>>>() {});
        assertThat(result.data()).containsExactly(new User(1,"A"));
    }

    @Test void postSerializesJson() {
        server.expect(requestTo("https://provider.example/users"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"name\":\"A\"}"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"A\"}",MediaType.APPLICATION_JSON));
        assertThat(client.post("/users", Map.of("name","A"), User.class).id()).isEqualTo(1);
    }

    @Test void exchangePreservesStatusHeadersAndSupportsVoid() {
        server.expect(requestTo("https://provider.example/users/1"))
                .andExpect(method(HttpMethod.DELETE)).andExpect(header("Authorization","Bearer dynamic"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT).header("X-Request-ID","123"));
        var response = client.exchange(HttpMethod.DELETE,"/users/{id}",null,
                h -> h.setBearerAuth("dynamic"), new ParameterizedTypeReference<Void>() {},1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("123");
        assertThat(response.getBody()).isNull();
    }

    @Test void emptyBodyReturnsNull() {
        server.expect(requestTo("https://provider.example/empty")).andRespond(withNoContent());
        assertThat(client.get("/empty", User.class)).isNull();
    }

    @ParameterizedTest @ValueSource(ints={400,401,403,404,409,429,500,502,503,504})
    void errorsPropagateWithoutRetry(int status) {
        server.expect(requestTo("https://provider.example/fail")).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertThatThrownBy(() -> client.get("/fail",User.class)).isInstanceOf(RestClientResponseException.class)
                .satisfies(e -> assertThat(((RestClientResponseException)e).getStatusCode().value()).isEqualTo(status));
    }

    @Test void transportFailurePropagates() {
        server.expect(requestTo("https://provider.example/fail"))
                .andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.get("/fail",User.class)).isInstanceOf(ResourceAccessException.class);
    }

    @Test void malformedJsonPropagates() {
        server.expect(requestTo("https://provider.example/broken")).andRespond(withSuccess("{broken",MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.get("/broken",User.class)).isInstanceOf(RestClientException.class);
    }

    @ParameterizedTest @ValueSource(strings={"https://evil.example/x","//evil.example/x","relative","","/bad\\host","/bad\npath"})
    void invalidPathsRejectedBeforeNetwork(String path) {
        assertThatIllegalArgumentException().isThrownBy(() -> client.get(path,User.class));
    }

    @Test void missingUriVariableRejectedBeforeNetwork() {
        assertThatIllegalArgumentException().isThrownBy(() -> client.get("/users/{id}",User.class));
    }

    @Test void unsupportedContentTypeFailsConversion() {
        server.expect(requestTo("https://provider.example/text"))
                .andRespond(withSuccess("plain text",MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.get("/text",User.class)).isInstanceOf(RestClientException.class);
    }

    @Test void requestHeadersDoNotLeakToNextCall() {
        server.expect(requestTo("https://provider.example/first"))
                .andExpect(header("X-Correlation-ID","one")).andRespond(withNoContent());
        server.expect(requestTo("https://provider.example/second"))
                .andExpect(headerDoesNotExist("X-Correlation-ID")).andRespond(withNoContent());
        client.exchange(HttpMethod.GET,"/first",null,h -> h.set("X-Correlation-ID","one"),
                new ParameterizedTypeReference<Void>() {});
        client.get("/second",Void.class);
    }

    @Test void nullArgumentsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new RestApiClient(null));
        assertThatNullPointerException().isThrownBy(() -> client.get(null,User.class));
        assertThatNullPointerException().isThrownBy(() -> client.get("/x",(Class<User>)null));
        assertThatNullPointerException().isThrownBy(() -> client.get("/x",(ParameterizedTypeReference<User>)null));
        assertThatNullPointerException().isThrownBy(() -> client.post("/x",null,User.class));
        assertThatNullPointerException().isThrownBy(() -> client.exchange(null,"/x",null,h->{},new ParameterizedTypeReference<Void>(){}));
        assertThatNullPointerException().isThrownBy(() -> client.exchange(HttpMethod.GET,"/x",null,null,new ParameterizedTypeReference<Void>(){}));
        assertThatNullPointerException().isThrownBy(() -> client.get("/x",User.class,(Object[])null));
    }

    @ParameterizedTest @ValueSource(strings={"ftp://host","/relative","https://u:p@host","https://host?q=x","https://host/#x"})
    void invalidBaseUrlsRejected(String url) {
        assertThatIllegalArgumentException().isThrownBy(() -> settings(url));
    }
    private ProviderSettings settings(String url) {
        return new ProviderSettings(URI.create(url),Duration.ofSeconds(1),Duration.ofSeconds(2));
    }
    @Test void invalidTimeoutsAndNullSettingsRejected() {
        for(var duration: List.of(Duration.ZERO,Duration.ofSeconds(-1))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProviderSettings(URI.create("https://host"),duration,Duration.ofSeconds(1)));
            assertThatIllegalArgumentException().isThrownBy(() -> new ProviderSettings(URI.create("https://host"),Duration.ofSeconds(1),duration));
        }
        assertThatNullPointerException().isThrownBy(() -> new ProviderSettings(null,Duration.ofSeconds(1),Duration.ofSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> new ProviderSettings(URI.create("https://host"),null,Duration.ofSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> new ProviderSettings(URI.create("https://host"),Duration.ofSeconds(1),null));
        assertThatNullPointerException().isThrownBy(() -> ApiClientFactory.create(null,settings("https://host"),b->{}));
        assertThatNullPointerException().isThrownBy(() -> ApiClientFactory.create(RestClient.builder(),null,b->{}));
        assertThatNullPointerException().isThrownBy(() -> ApiClientFactory.create(RestClient.builder(),settings("https://host"),null));
    }

    /** Each cloned builder gets its own headers and host; prototype stays untouched. */
    @Test void factoryIsolatesProviderConfiguration() {
        var prototype = RestClient.builder();
        var first = ApiClientFactory.create(prototype,settings("https://one.example"),b -> b.defaultHeader("X-Provider","one")
                .requestInterceptor((req,body,execution) -> {
                    assertThat(req.getURI().getHost()).isEqualTo("one.example");
                    assertThat(req.getHeaders().getFirst("X-Provider")).isEqualTo("one");
                    throw new java.io.IOException("intercepted");
                }));
        var second = ApiClientFactory.create(prototype,settings("https://two.example"),b -> b
                .requestInterceptor((req,body,execution) -> {
                    assertThat(req.getURI().getHost()).isEqualTo("two.example");
                    assertThat(req.getHeaders().containsKey("X-Provider")).isFalse();
                    throw new java.io.IOException("intercepted");
                }));
        assertThatThrownBy(() -> first.get("/x",String.class)).isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> second.get("/x",String.class)).isInstanceOf(ResourceAccessException.class);
    }
}
