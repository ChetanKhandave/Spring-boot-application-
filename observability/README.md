# Run the observability stack

The backends run in Docker Compose while Java applications run on the host (terminals/STS). Install Docker with Compose v2; Windows/macOS use Docker Desktop. All published backend ports are bound to localhost. This is a local development stack.

From the repository root, set a Grafana password and start the backends:

```powershell
# Windows PowerShell
$env:GRAFANA_ADMIN_PASSWORD = 'choose-your-local-password'
New-Item -ItemType Directory -Force logs
docker compose -f observability/compose.yml up -d
```

```sh
# Linux / macOS
export GRAFANA_ADMIN_PASSWORD='choose-your-local-password'
mkdir -p logs
docker compose -f observability/compose.yml up -d
```

Build the Java modules with `mvn clean verify -DskipITs`, then follow the root README to start Config Server, Eureka, and service instances. Run from the repository root so structured JSON files go into `logs/`, which Alloy mounts read-only. For STS set the working directory to the repository root. If you override `LOG_DIRECTORY`, update the Alloy bind mount too. Logs rotate; Alloy tails active `.log` files and persists read positions.

| Tool | Local URL | Purpose |
|---|---|---|
| Grafana | http://localhost:3000 | Login `admin` and your chosen password; provisioned Architecture A dashboard |
| Prometheus | http://localhost:9090 | Metrics, targets, alert rules |
| Alertmanager | http://localhost:9093 | Active alerts, grouping and silences |
| Tempo | http://localhost:3200/ready | Trace backend; browse through Grafana Explore |
| Loki | http://localhost:3100/ready | Log backend; browse through Grafana Explore |

## Metrics and health

Actuator + Micrometer Prometheus registry are enabled on all six Java modules. Each application exposes `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/prometheus`. Readiness is a Spring availability signal; it does not prove every downstream dependency is working. Prometheus `up` indicates successful scraping, not complete business health.

Prometheus scrapes eight mandatory JVM instances plus optional employee instance 8092 using `host.docker.internal`. Compose adds the host-gateway mapping for Linux. For remote hosts or different ports, edit `prometheus/prometheus.yml`; targets are static and do not automatically follow Eureka. If employee 8092 is not started, remove its target to avoid an expected down alert. If Docker cannot reach host apps, check firewall/network settings. A host-only loopback binding can prevent container scraping.

Grafana's provisioned dashboard shows availability, request rate, P95 latency, 5xx rate, JVM heap, CPU, threads and centralized logs. Histograms use bounded route templates; do not put request IDs or user IDs into metric labels.

## Distributed tracing

Micrometer Tracing bridges observations to OpenTelemetry and exports OTLP/HTTP to Tempo at `http://localhost:4318/v1/traces`. W3C `traceparent` context propagates through Gateway's observed HTTP client to the business service. In Grafana Explore select Tempo and search by `service.name` (e.g. `student-service`) or paste a trace ID from the logs. One gateway request should contain Gateway and service spans.

`TRACE_SAMPLING_PROBABILITY` defaults to 1.0 for this demo. Tune it for production. `OTLP_TRACING_ENDPOINT` overrides the exporter address. Config Server needs local observability properties because it boots independently; other apps receive them centrally. Metrics remain available even if Tempo is stopped; exporter failures can produce diagnostic logs.

## Centralized logs and correlation

Spring Boot's built-in Logstash JSON format includes service, instance, level and Micrometer MDC traceId/spanId when a traced operation is active. Business controllers log a processing event without logging request bodies or personal data. Startup/background events may have no trace ID.

Grafana Alloy tails `logs/*.log`, extracts low-cardinality labels, and sends records to Loki. In Grafana Explore select Loki:

```logql
{service="student-service"} | json
{service="student-service"} |= "YOUR_TRACE_ID"
{job="spring-boot", level="ERROR"}
```

Expand a log with `traceId` to use the View trace link. The Tempo datasource also supports trace-to-log navigation. Trace IDs stay in the log body rather than indexed labels. Tempo local retention is 24 hours; Loki retention is 7 days. These demo filesystem backends are not HA.

## Alerts and notifications

Prometheus evaluates instance-down, high 5xx rate, high P95 latency and high heap usage rules and forwards firing alerts to Alertmanager. Down alerts wait 1 minute; other rules have longer windows to reduce noise. Stop a monitored instance to test down detection; restart it to observe resolution.

The default `local-ui` receiver has no external destination: alerts are visible in Alertmanager and the Grafana Alertmanager datasource, but email/Slack/webhook notifications are not sent. To enable delivery, configure an actual receiver in `alertmanager/alertmanager.yml`; `webhook-example.yml` shows the shape. Keep tokens/passwords outside Git, use receiver secret-file options where supported, and restart Alertmanager. No real notification destination or credentials are assumed.

## Verification

Normal `mvn clean verify` checks the full Java architecture and metrics endpoints without requiring Docker. For full ingestion verification, start Compose, stop manually started Java demo apps, then:

```powershell
$env:OBSERVABILITY_VERIFY = 'true'
mvn clean verify
```

```sh
OBSERVABILITY_VERIFY=true mvn clean verify
```

The Java integration test verifies remote configuration, discovery/load balancing, Prometheus scraping all eight mandatory JVMs, Tempo storing a trace containing both Gateway and Student Service, Loki storing a log with the same trace ID, Grafana health, loaded Prometheus rules and Alertmanager accepting a synthetic local alert. CI runs these checks and uploads application/backend logs. The synthetic test alert is informational; it does not exercise an external notification receiver.

Backend syntax checks:

```sh
docker compose -f observability/compose.yml run --rm --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose -f observability/compose.yml run --rm --entrypoint amtool alertmanager check-config /etc/alertmanager/alertmanager.yml
```

Stop with `docker compose -f observability/compose.yml down`. Named volumes preserve data. Removing volumes deletes stored telemetry. Grafana credentials are initialized on first startup; changing the environment variable does not reset an existing account password.

Before shared deployment, secure Actuator/backend access and add TLS/authentication. Loki/Tempo here are local unauthenticated backends; never publish them directly. Images are pinned for reproducibility; review upgrades and security fixes before production use. Provisioned Grafana datasource credentials are not committed.
