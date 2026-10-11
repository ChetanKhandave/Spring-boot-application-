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
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Verifies all three routes and equal selection across three stable student instances. */
    @Test
    void discoversServicesAndBalancesRequests() throws Exception {
        // Check all ports before starting anything; never terminate existing user processes.
        requireFreePorts(8762, 8761, 8080, 8081, 8082, 8083, 8091, 8101);
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
            System.out.println("PASS: centralized configuration, real Eureka discovery, three gateway routes, round-robin distribution: " + counts);
        } finally {
            stopApplications();
            System.out.println("Application logs: " + logDirectory);
        }
    }

    private void start(String module, int port) throws IOException {
        Path jar = root.resolve(module + "/target/" + module + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Missing " + jar + "; run mvn clean verify from the repository root");
        }
        Files.createDirectories(logDirectory);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(java.toString(), "-Xmx256m", "-jar", jar.toString(),
                "--server.port=" + port)
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
