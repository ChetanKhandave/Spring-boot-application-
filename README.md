# Architecture A: Spring Cloud microservices

Working example of **Eureka + Spring Cloud Gateway + Spring Cloud LoadBalancer**.
Java 17, Spring Boot 3.5.16 and Spring Cloud 2025.0.3, with Maven BOM dependency management.
These versions follow the Boot 3.x context of this example. Spring Cloud 2025.0.x open source support ended June 30, 2026; evaluate a supported release train before production use.

| Module | Port(s) | Responsibility |
|---|---|---|
| config-server | 8762 | Central configuration for all five client applications |
| eureka-server | 8761 | Service registry and dashboard |
| api-gateway | 8080 | Path routing and client-side load balancing |
| student-service | 8081, 8082, 8083 | Demo student endpoint |
| employee-service | 8091, 8092 optional | Demo employee endpoint |
| college-service | 8101 | Demo college endpoint |

Each service registers its logical name and instance address. Gateway obtains available instances via the Eureka client and selects one with Spring Cloud LoadBalancer. Eureka is not in the business-request forwarding path. Each gateway process has its own load balancer and registry cache.

## Build

Install JDK 17+ and Maven 3.6.3+; ensure `JAVA_HOME` and PATH are set.

```sh
mvn clean verify
```

## Run (Windows, Linux or macOS)

Run each command in a separate terminal from the repository root. Start Config Server first and wait for http://localhost:8762/actuator/health, then Eureka and its dashboard at http://localhost:8761, then services and gateway. Run from the repository root so Config Server finds the external `configurations/` folder.

```sh
java -jar config-server/target/config-server.jar
java -jar eureka-server/target/eureka-server.jar
java -jar student-service/target/student-service.jar --server.port=8081
java -jar student-service/target/student-service.jar --server.port=8082
java -jar student-service/target/student-service.jar --server.port=8083
java -jar employee-service/target/employee-service.jar --server.port=8091
java -jar employee-service/target/employee-service.jar --server.port=8092
java -jar college-service/target/college-service.jar
java -jar api-gateway/target/api-gateway.jar
```

Wait until Eureka lists the instances as UP and gateway discovery caches refresh (often 30–90 seconds). Visit:

- http://localhost:8080/students/101
- http://localhost:8080/employees/101
- http://localhost:8080/colleges/101

Example response:

```json
{"id":101,"name":"Student 101","service":"student-service","instancePort":"8082","configurationProfile":"dev"}
```

All positive IDs return illustrative data; no database is used. Invalid or nonpositive IDs return HTTP 400.
Request the student URL repeatedly to observe different `instancePort` values. In PowerShell:

```powershell
1..12 | ForEach-Object { Invoke-RestMethod http://localhost:8080/students/101 }
```

The default selection is round-robin; it balances instance selections, not CPU usage or active request counts. Starting another instance needs no gateway route change.

## STS / Eclipse

Import **Existing Maven Projects**, select this root directory and import all modules. Use JDK 17+, Maven > Update Project, then Run As > Spring Boot App for each application. Duplicate the StudentServiceApplication run configuration and add `--server.port=8082` or `--server.port=8083` under Program arguments. Use separate run configurations for each instance.

## Configuration

Client bootstrap settings (application name, profile, Config Server URL) remain in each module's `src/main/resources/application.properties`. Service ports, discovery settings, gateway routes and shared settings are now in the external `configurations/` folder. Gateway uses the release-specific prefix `spring.cloud.gateway.server.webflux.routes` and the reactive `spring-cloud-starter-gateway-server-webflux` starter. Do not add `spring-boot-starter-web` to the reactive gateway.

Routes use `lb://student-service`, `lb://employee-service`, and `lb://college-service`; there are no backend instance addresses in route configuration. Explicit routes keep the public API controlled; discovery-locator automatic routes are not enabled. Incoming paths are preserved.

`EUREKA_URL` overrides the registry URL. The default `eureka.instance.hostname=localhost` is for a single-machine demo; for VMs/containers, override it with a reachable hostname or configure `prefer-ip-address=true` with the correct IP. `localhost` in a container points to that container. Instance IDs combine service name and port; customize IDs when several hosts share a port.

Actuator exposes health/info/prometheus, with liveness/readiness probes and JVM/HTTP metrics. Gateway outbound connect timeout is 3 seconds and response timeout is 5 seconds. No automatic retry is configured.

## Centralized configuration

Config Server uses the native filesystem backend, serving files from `configurations/` in this checkout. Configuration changes are tracked in Git, but the server reads the local checkout, not GitHub directly. Pull updates on the Config Server host before using them.

- `application.properties`: shared client settings.
- `<service>.properties`: service-specific defaults.
- `<service>-dev.properties` and `<service>-prod.properties`: profile overrides for all five clients, including Eureka and Gateway.

`dev` is the default client profile; use `--spring.profiles.active=prod` or `APP_PROFILE=prod` to select production overrides. The prod files are demonstration overlays, not production-hardened infrastructure configuration. Config Server uses its own `native` profile and local bootstrap configuration, avoiding a circular dependency.

Inspect the served configuration at http://localhost:8762/student-service/dev or http://localhost:8762/api-gateway/prod. The required `spring.config.import=configserver:...` makes client startup fail if Config Server is unavailable. `CONFIG_SERVER_URL` overrides its URL. Config Server does not register with Eureka; clients reach it directly, so startup order is Config Server → Eureka → business services/Gateway.

To run Config Server from another working directory, specify an absolute folder URI:

```sh
java -jar config-server/target/config-server.jar --spring.cloud.config.server.native.search-locations=file:/absolute/path/configurations/
```

For Windows use `file:///C:/projects/Spring-boot-application-/configurations/` (quote the argument if the path contains spaces). The `CONFIG_LOCATION` environment variable accepts the same URI. In STS, set Config Server's working directory to the repository root or pass this search-locations argument.

Edit central properties and restart affected clients to apply updates. Automatic refresh and Spring Cloud Bus are not configured. Command-line arguments such as `--server.port=8082` still override remote values. Controller unit tests disable Config Client to remain independent of external servers; the integration test uses the real server.

Keep Config Server private and add authentication/TLS before deployment: its endpoints expose configuration. No credentials or secrets are committed. For high availability, use a reliable shared configuration backend across Config Server instances.

## Tests

`mvn clean verify` runs the nine MVC response-contract/invalid-ID tests and then the Java end-to-end smoke test in the `integration-tests` module. Maven Failsafe executes `ArchitectureASmokeIT` after the application JARs are packaged. Python is not required.

Stop all manually started demo apps before running:

```sh
mvn clean verify
```

The Java test first starts Config Server, verifies dev/prod configuration for every client, and checks that Student Service consumes the remote profile marker. It uses `ProcessBuilder` to launch Eureka, three student instances, employee, college and gateway. Java 17 `HttpClient` sends actual HTTP requests; Jackson reads JSON and JUnit checks the responses. It waits for discovery, verifies all three gateway routes, and asserts that 12 sequential student requests reach each of three stable instances four times. It stops its own processes even after a failure and keeps application logs under `integration-tests/target/smoke-logs`.

Requires ports 8762, 8761, 8080–8083, 8091 and 8101 free and approximately 2 GB free RAM. Run the Maven command from the repository root. To build and run only unit tests without starting the full architecture:

```sh
mvn clean verify -DskipITs
```

GitHub Actions runs the same Java/Maven test flow and uploads test reports and application logs, including when verification fails.

## Failure behavior and production scope

Stop one student instance and repeat requests. Graceful shutdown deregisters; crashes require lease expiration and cache refresh. During convergence requests may fail. Eureka self-preservation may retain stale registrations; load balancing does not promise immediate failover. No retry, circuit breaker, authentication, TLS or production HA is included in this learning example. When no instance is available, gateway normally returns HTTP 503. Unknown routes return HTTP 404.

For production, configure registry and gateway redundancy, reachable addresses, authentication/authorization, TLS, observability and resilience appropriate to the API. Keep backend access controlled. Never blindly retry non-idempotent writes.

References: [Spring Cloud compatibility](https://spring.io/projects/spring-cloud), [2025.0.3 release and lifecycle](https://spring.io/blog/2026/06/11/spring-cloud-2025-0-3-aka-northfields-has-been-released/), [Gateway 4.3 docs](https://docs.spring.io/spring-cloud-gateway/reference/4.3/).

## Observability

All Java applications now include Actuator, Micrometer Prometheus metrics, Micrometer Tracing with OpenTelemetry OTLP export, and structured JSON logging. The `observability/` folder provides Docker Compose for Prometheus, Grafana, Tempo, Loki, Grafana Alloy and Alertmanager, with provisioned data sources, a dashboard and alert rules. See [observability setup](observability/README.md) for startup commands, trace/log correlation, notifications and end-to-end ingestion verification.

## OpenAPI documentation and Swagger UI

The business services use `springdoc-openapi-starter-webmvc-ui`; reactive Gateway uses `springdoc-openapi-starter-webflux-ui`. Version 2.8.17 is managed in the parent POM for Spring Boot 3.5 compatibility. Infrastructure services (Config Server and Eureka) expose health/metrics rather than business Swagger endpoints.

With the default `dev` profile, start Config Server, Eureka, services and Gateway, then open **http://localhost:8080/swagger-ui.html**. Select Student, Employee or College Service in the definition dropdown, expand the GET operation, choose **Try it out**, enter `101`, and click **Execute**. Requests go through Gateway and can return different instance ports. Enter `0` or a nonnumeric ID to test HTTP 400. The endpoints return illustrative data; there is no database record lookup or HTTP 404 for an unknown positive ID.

| Service | Direct Swagger UI | Direct OpenAPI JSON | OpenAPI through Gateway |
|---|---|---|---|
| Student | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs | http://localhost:8080/openapi/students |
| Employee | http://localhost:8091/swagger-ui.html | http://localhost:8091/v3/api-docs | http://localhost:8080/openapi/employees |
| College | http://localhost:8101/swagger-ui.html | http://localhost:8101/v3/api-docs | http://localhost:8080/openapi/colleges |

Gateway presents three definitions rather than merging their contracts. Dev-only load-balanced documentation routes rewrite `/openapi/<service>` to the selected service's `/v3/api-docs`. Relative OpenAPI server URL `/` makes Try it out use the current browser origin, so direct UI calls the service and Gateway UI calls Gateway without adding CORS configuration.

Contracts document operation descriptions, the positive ID parameter, HTTP 200/400 responses and response field meanings/examples. Swagger UI enables manual interactive API calls; it does not replace automated tests. Maven verification checks contracts, UI pages, Gateway aggregation, invalid-ID responses and documentation shutdown with real `prod` applications.

For `prod` (and profiles other than explicitly enabled `dev`), API-docs and UI are disabled by default. Gateway documentation routes are configured only in the dev overlay. Start every client with the intended profile, e.g. `--spring.profiles.active=prod`; mixing dev clients with a prod Gateway can still leave direct dev service documentation accessible. No authentication is added by Swagger itself. Keep dev services private; if security is added later, configure documentation access according to the chosen authentication policy.

## Shared outbound HTTP client

The MVC services depend on [`common-http-client`](common-http-client/README.md). See its guide for separate provider configurations, typed responses, authentication extensions and test commands.
