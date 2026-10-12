package com.chetan.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Starts real JVMs and verifies discovery, public gateway routes and round-robin selection.
 * Run from the repository root using {@code mvn clean verify}. No Python is required.
 * Application logs remain in integration-tests/target/smoke-logs for diagnosis.
 */
class ArchitectureASmokeIT {
    private final Path root = Path.of(System.getProperty("architecture.root"));
    private final Path logDirectory = root.resolve("integration-tests/target/smoke-logs");
    private final List<Process> processes = new ArrayList<>();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Verifies all three routes and equal selection across three stable student instances. */
    @Test
    void discoversServicesAndBalancesRequests() throws Exception {
        // Check all ports before starting anything; never terminate existing user processes.
        requireFreePorts(8762, 8761, 8080, 8081, 8082, 8083, 8091, 8101, 8180, 8181, 8191, 8201);
        try {
            start("config-server", 8762);
            await(() -> "UP".equals(get(8762, "/actuator/health").path("status").asText()),
                    "Config Server startup");
            // Verify service/profile resolution before any clients are launched.
            for (String service : List.of("eureka-server", "api-gateway", "student-service",
                    "employee-service", "college-service")) {
                for (String profile : List.of("dev", "prod")) {
                    JsonNode config = get(8762, "/" + service + "/" + profile);
                    assertEquals(service, config.path("name").asText());
                    boolean found = false;
                    for (JsonNode source : config.path("propertySources")) {
                        if (profile.equals(source.path("source").path("app.configuration.profile").asText())) {
                            found = true;
                        }
                    }
                    assertEquals(true, found, "Missing profile configuration for " + service + "/" + profile);
                }
            }
            start("eureka-server", 8761);
            await(() -> "UP".equals(get(8761, "/actuator/health").path("status").asText()),
                    "Eureka startup");
            for (int port : new int[]{8081, 8082, 8083}) {
                start("student-service", port);
            }
            start("employee-service", 8091);
            start("college-service", 8101);
            start("api-gateway", 8080);

            // Discovery is eventually consistent; readiness is polled rather than assumed.
            await(() -> "student-service".equals(get(8080, "/students/101").path("service").asText()),
                    "student route discovery");
            Set<String> expectedPorts = Set.of("8081", "8082", "8083");
            await(() -> sampleStudents().keySet().equals(expectedPorts), "all three student instances");
            Map<String, Integer> counts = sampleStudents();
            assertEquals(Map.of("8081", 4, "8082", 4, "8083", 4), counts,
                    "Twelve sequential requests should be evenly distributed");
            await(() -> "employee-service".equals(get(8080, "/employees/101").path("service").asText()),
                    "employee route discovery");
            await(() -> "college-service".equals(get(8080, "/colleges/101").path("service").asText()),
                    "college route discovery");
            for (int port : new int[]{8762, 8761, 8080, 8081, 8082, 8083, 8091, 8101}) {
                JsonNode metrics = get(port, "/actuator/metrics");
                org.junit.jupiter.api.Assertions.assertTrue(metrics.path("names").toString().contains("jvm.memory.used"));
                JsonNode memory = get(port, "/actuator/metrics/jvm.memory.used");
                org.junit.jupiter.api.Assertions.assertFalse(memory.path("measurements").isEmpty());
                assertEquals(404, status(port, "/actuator/prometheus"));
            }
            System.out.println("PASS: built-in Actuator metrics on all JVMs without external backends");
            verifyOpenApi();
            System.out.println("PASS: centralized configuration, real Eureka discovery, three gateway routes, round-robin distribution: " + counts);
        } finally {
            stopApplications();
            System.out.println("Application logs: " + logDirectory);
        }
    }

    private void start(String module, int port) throws IOException {
        start(module, port, "dev");
    }

    private void start(String module, int port, String profile) throws IOException {
        Path jar = root.resolve(module + "/target/" + module + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Missing " + jar + "; run mvn clean verify from the repository root");
        }
        Files.createDirectories(logDirectory);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(java.toString(), "-Xmx256m", "-jar", jar.toString(),
                "--server.port=" + port, "--spring.profiles.active=" + (module.equals("config-server") ? "native" : profile))
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logDirectory.resolve(module + "-" + port + ".log").toFile())
                .start();
        processes.add(process);
    }

    private JsonNode get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + path + " returned HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    /** Checks API contracts/UI assets, Gateway aggregation and production documentation shutdown. */
    private void verifyOpenApi() throws Exception {
        String[][] services = {{"student-service", "students", "8081", "8181"},
                {"employee-service", "employees", "8091", "8191"},
                {"college-service", "colleges", "8101", "8201"}};
        for (String[] service : services) {
            int port = Integer.parseInt(service[2]);
            String path = "/" + service[1] + "/{id}";
            JsonNode direct = get(port, "/v3/api-docs");
            assertEquals("3.1.0", direct.path("openapi").asText());
            org.junit.jupiter.api.Assertions.assertTrue(direct.path("paths").has(path));
            JsonNode operation = direct.path("paths").path(path).path("get");
            org.junit.jupiter.api.Assertions.assertTrue(operation.path("responses").has("200"));
            org.junit.jupiter.api.Assertions.assertTrue(operation.path("responses").has("400"));
            assertEquals("/", direct.path("servers").path(0).path("url").asText());
            org.junit.jupiter.api.Assertions.assertTrue(read(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/swagger-ui.html")).GET().build()).contains("Swagger UI"));
            JsonNode gateway = get(8080, "/openapi/" + service[1]);
            assertEquals(direct.path("info"), gateway.path("info"));
            assertEquals(400, status(8080, "/" + service[1] + "/0"));
        }
        assertEquals(3, get(8080, "/v3/api-docs/swagger-config").path("urls").size());
        org.junit.jupiter.api.Assertions.assertTrue(read(HttpRequest.newBuilder(
                URI.create("http://localhost:8080/swagger-ui.html")).GET().build()).contains("Swagger UI"));
        // Production uses the real centrally served prod profile; services remain functional.
        for (String[] service : services) {
            int port = Integer.parseInt(service[3]);
            start(service[0], port, "prod");
            await(() -> "UP".equals(get(port, "/actuator/health").path("status").asText()), "prod " + service[0]);
            assertEquals("prod", get(port, "/" + service[1] + "/101").path("configurationProfile").asText());
            assertEquals(404, status(port, "/v3/api-docs"));
            assertEquals(404, status(port, "/swagger-ui/index.html"));
        }
        start("api-gateway", 8180, "prod");
        await(() -> "UP".equals(get(8180, "/actuator/health").path("status").asText()), "prod gateway");
        assertEquals(404, status(8180, "/v3/api-docs"));
        assertEquals(404, status(8180, "/swagger-ui/index.html"));
        for (String[] service : services) {
            assertEquals(404, status(8180, "/openapi/" + service[1]));
        }
        System.out.println("PASS: OpenAPI contracts, Swagger UI, Gateway docs aggregation, invalid-ID calls and prod docs disabled");
    }

    private int status(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String read(HttpRequest request) throws IOException, InterruptedException {
        HttpRequest timed = HttpRequest.newBuilder(request, (name, value) -> true)
                .timeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(timed, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + request.uri());
        }
        return response.body();
    }

    private Map<String, Integer> sampleStudents() throws IOException, InterruptedException {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            JsonNode response = get(8080, "/students/101");
            assertEquals("student-service", response.path("service").asText());
            assertEquals(101, response.path("id").asInt());
            assertEquals("dev", response.path("configurationProfile").asText(), "Client must consume remote properties");
            counts.merge(response.path("instancePort").asText(), 1, Integer::sum);
        }
        return counts;
    }

    /** Retries transient HTTP/discovery failures, preserving the last cause on timeout. */
    private void await(Check check, String description) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
        Exception lastFailure = null;
        while (System.nanoTime() < deadline) {
            for (Process process : processes) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("Application exited with code " + process.exitValue()
                            + "; inspect " + logDirectory);
                }
            }
            try {
                if (check.ready()) {
                    return;
                }
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (IOException transientFailure) {
                lastFailure = transientFailure;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Timed out waiting for " + description + "; inspect " + logDirectory,
                lastFailure);
    }

    private static void requireFreePorts(int... ports) throws IOException {
        for (int port : ports) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port));
            } catch (IOException failure) {
                throw new IOException("Port " + port + " is unavailable; stop manually started demo apps", failure);
            }
        }
    }

    /** Cleans up only JVMs started by this test, including after startup or assertion failure. */
    private void stopApplications() {
        boolean interrupted = false;
        for (int i = processes.size() - 1; i >= 0; i--) {
            processes.get(i).destroy();
        }
        for (Process process : processes) {
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException failure) {
                process.destroyForcibly();
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ready() throws IOException, InterruptedException;
    }
}
