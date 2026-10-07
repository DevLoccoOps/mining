-- MineSafe RTLS - continuous telemetry storage (V2)
--
-- Durable per-reading record of consumed BLE telemetry. Alert-only rows live
-- in alert_logs; this table captures every reading (position, environment,
-- battery) so history can be analysed. Capped by a time-based retention job
-- (see TelemetryService.enforceRetention).

CREATE TABLE telemetry (
    id         BIGSERIAL PRIMARY KEY,
    mac        TEXT NOT NULL,
    miner_name TEXT,
    zone       TEXT,
    reporter   TEXT,
    dist_m     NUMERIC(10, 2),
    temp_c     NUMERIC(6, 2),
    battery    INT,
    rssi       NUMERIC(6, 2),
    acc_x      NUMERIC(6, 3),
    acc_y      NUMERIC(6, 3),
    acc_z      NUMERIC(6, 3),
    outdoor    BOOLEAN,
    moving     BOOLEAN,
    registered BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_telemetry_created     ON telemetry (created_at DESC, id DESC);
CREATE INDEX idx_telemetry_mac_created ON telemetry (mac, created_at DESC);
