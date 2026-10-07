# MineSafe Backend

Real-time location and safety monitoring backend for underground/surface mining
BLE tag telemetry. A Java 21 / Spring Boot 3.4 port of the legacy Python
`app.py` service, consuming the same MQTT feed and exposing the same API shape.

## Architecture

```
MikroTik KNOT gateways
        │  BLE advertisements
        ▼
MQTT broker (topic: knot/ble/tags)
        │  Camel paho route (MQTT 3.1.1)
        ▼
TelemetryIngester ──► BlePayloadParser   (hex frame → temp/battery/accel/txPower)
        │            SafetyEngine        (impact, fall, immobility, heat stress)
        │            MinerStateStore     (in-memory snapshots, handover hysteresis)
        ├─► PostgreSQL: alert_logs (alerts only), telemetry (every reading)
        ▼
StateBroadcaster (500 ms tick) ──► WebSocket /ws/state (zone-grouped state_update)
```

Key components:

| Component | Purpose |
|---|---|
| `route/MqttIngestRoute` | Subscribes to the BLE topic, hands each payload to the ingester |
| `service/TelemetryIngester` | Per-message pipeline: parse → smooth RSSI (EMA) → lookup → decode → evaluate → persist |
| `service/SafetyEngine` | Stateful per-MAC evaluation: impact/fall, immobility, heat stress |
| `service/MinerStateStore` | Live miner snapshots with handover hysteresis and stale eviction |
| `service/StateBroadcaster` | 500 ms aggregated `state_update` broadcast + hourly retention cleanups |
| `ws/LiveHub` | WebSocket fan-out to connected dashboards |

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL with a `minesafe` database (Flyway applies the schema on startup)

## Configuration

All values can be overridden via environment variables (see
`src/main/resources/application.properties`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/minesafe` | JDBC URL |
| `DB_USERNAME` | `lebohang` | Database user |
| `DB_PASSWORD` | *(empty)* | Database password |
| `MQTT_BROKER_URL` | `tcp://sakura.proxy.rlwy.net:50528` | Broker URL (MQTT 3.1.1) |
| `MQTT_TOPIC` | `knot/ble/tags` | BLE telemetry topic |
| `MQTT_CLIENT_ID` | `minesafe-spring` | MQTT client id |
| `ALERTS_MAX_RETENTION` | `50000` | Max `alert_logs` rows kept |
| `TELEMETRY_RETENTION_HOURS` | `24` | Hours of `telemetry` rows kept |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Allowed origins for REST **and** WebSocket |

## Running

```bash
createdb minesafe          # once
mvn spring-boot:run
```

The REST API is served on port 8080; OpenAPI docs at
`http://localhost:8080/swagger-ui.html`.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/personnel` | List personnel |
| POST | `/api/personnel` | Create/update personnel (`id` null = create) |
| GET | `/api/personnel/by-mac/{mac}` | Find personnel by assigned tag MAC |
| DELETE | `/api/personnel/{id}` | Remove personnel |
| GET | `/api/tags` | List registered tags |
| POST | `/api/tags` | Create/update a tag; `action=assign`/`unassign` binds it to personnel |
| DELETE | `/api/tags/{mac}` | Remove a tag |
| GET | `/api/logs` | 100 most recent alert logs |
| WS | `/ws/state` | Live `state_update` push every 500 ms |

All JSON uses snake_case, matching the legacy Python API.

## MAC address format

MACs are normalized everywhere (registry writes and telemetry lookups) to the
bare 12-hex-digit uppercase form — `AABBCCDDEEFF`. Inputs with `:` or `-`
separators are accepted and normalized on write, so a MAC entered as
`AA:BB:CC:DD:EE:FF` still matches incoming telemetry.

## Tests

```bash
mvn test
```

Unit tests cover the BLE payload parser, safety engine, state store, MAC
normalization, and the full ingestion pipeline against a mocked persistence
layer.