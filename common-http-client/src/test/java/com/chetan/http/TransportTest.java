package com.chetan.http;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.*;
import static org.assertj.core.api.Assertions.*;

/** Loopback tests verify the real JDK transport rather than mocking its behavior. */
class TransportTest {
    @Test void concurrentCallsUseDynamicAuthenticationAndTimeoutIsEnforced() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        var handlers = Executors.newCachedThreadPool();
        var callers = Executors.newFixedThreadPool(4);
        server.setExecutor(handlers);
        server.createContext("/token", exchange -> {
            byte[] bytes = exchange.getRequestHeaders().getFirst("Authorization").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            entered.countDown();
            try { release.await(5,TimeUnit.SECONDS); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            URI url = URI.create("http://localhost:"+server.getAddress().getPort());
            var sequence = new AtomicInteger();
            var client = ApiClientFactory.create(RestClient.builder(),
                    new ProviderSettings(url,Duration.ofSeconds(2),Duration.ofSeconds(5)),
                    b -> b.requestInterceptor((request,body,execution) -> {
                        request.getHeaders().setBearerAuth("token-"+sequence.incrementAndGet());
                        return execution.execute(request,body);
                    }));
            var results = new java.util.ArrayList<Future<String>>();
            for(int i=0;i<8;i++) results.add(callers.submit(() -> client.get("/token",String.class)));
            var tokens = new java.util.HashSet<String>();
            for(var future: results) tokens.add(future.get(10,TimeUnit.SECONDS));
            assertThat(tokens).hasSize(8).allMatch(t -> t.startsWith("Bearer token-"));
            var shortClient = ApiClientFactory.create(RestClient.builder(),
                    new ProviderSettings(url,Duration.ofSeconds(2),Duration.ofMillis(200)),b->{});
            assertThatThrownBy(() -> shortClient.get("/slow",String.class)).isInstanceOf(ResourceAccessException.class);
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            server.stop(0);
            callers.shutdownNow();
            handlers.shutdownNow();
        }
    }
}
