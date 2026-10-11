# Architecture A: Spring Cloud microservices

Working example of **Eureka + Spring Cloud Gateway + Spring Cloud LoadBalancer**.
Java 17, Spring Boot 3.5.16 and Spring Cloud 2025.0.3, with Maven BOM dependency management.
These versions follow the Boot 3.x context of this example. Spring Cloud 2025.0.x open source support ended June 30, 2026; evaluate a supported release train before production use.

| Module | Port(s) | Responsibility |
|---|---|---|
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

Run each command in a separate terminal from the repository root. Start Eureka first and wait for its dashboard at http://localhost:8761, then start services and gateway.

```sh
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
{"id":101,"name":"Student 101","service":"student-service","instancePort":"8082"}
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

All application configuration is in each module's `src/main/resources/application.properties`. Gateway uses the release-specific prefix `spring.cloud.gateway.server.webflux.routes` and the reactive `spring-cloud-starter-gateway-server-webflux` starter. Do not add `spring-boot-starter-web` to the reactive gateway.

Routes use `lb://student-service`, `lb://employee-service`, and `lb://college-service`; there are no backend instance addresses in route configuration. Explicit routes keep the public API controlled; discovery-locator automatic routes are not enabled. Incoming paths are preserved.

`EUREKA_URL` overrides the registry URL. The default `eureka.instance.hostname=localhost` is for a single-machine demo; for VMs/containers, override it with a reachable hostname or configure `prefer-ip-address=true` with the correct IP. `localhost` in a container points to that container. Instance IDs combine service name and port; customize IDs when several hosts share a port.

Actuator exposes only health/info. Gateway outbound connect timeout is 3 seconds and response timeout is 5 seconds. No automatic retry is configured.

## Tests

`mvn verify` runs MVC response-contract and invalid-ID tests for all business services. For a real end-to-end check, stop all manually started demo apps, build, then run (Python 3 required):

```sh
python scripts/smoke-test.py
```

The script launches Eureka, three student instances, employee, college and gateway; waits for discovery; checks all three routes; asserts 12 sequential student requests reach each of three stable instances four times; and stops its own processes. It fails on startup or routing errors and prints application logs. Requires ports 8761, 8080–8083, 8091 and 8101 free and approximately 2 GB free RAM. CI runs both test layers.

## Failure behavior and production scope

Stop one student instance and repeat requests. Graceful shutdown deregisters; crashes require lease expiration and cache refresh. During convergence requests may fail. Eureka self-preservation may retain stale registrations; load balancing does not promise immediate failover. No retry, circuit breaker, authentication, TLS or production HA is included in this learning example. When no instance is available, gateway normally returns HTTP 503. Unknown routes return HTTP 404.

For production, configure registry and gateway redundancy, reachable addresses, authentication/authorization, TLS, observability and resilience appropriate to the API. Keep backend access controlled. Never blindly retry non-idempotent writes.

References: [Spring Cloud compatibility](https://spring.io/projects/spring-cloud), [2025.0.3 release and lifecycle](https://spring.io/blog/2026/06/11/spring-cloud-2025-0-3-aka-northfields-has-been-released/), [Gateway 4.3 docs](https://docs.spring.io/spring-cloud-gateway/reference/4.3/).
