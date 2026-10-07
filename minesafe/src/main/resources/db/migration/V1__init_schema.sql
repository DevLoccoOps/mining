-- MineSafe RTLS - initial schema

CREATE TABLE personnel (
    id           VARCHAR(10) PRIMARY KEY,
    name         TEXT NOT NULL,
    role         TEXT,
    shift        TEXT,
    assigned_mac VARCHAR(17) UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    mac         VARCHAR(17) PRIMARY KEY,
    type        TEXT NOT NULL CHECK (type IN ('indoor', 'outdoor')),
    assigned_to VARCHAR(10) REFERENCES personnel (id) ON DELETE SET NULL
);

CREATE TABLE alert_logs (
    id         BIGSERIAL PRIMARY KEY,
    mac        TEXT NOT NULL,
    miner_name TEXT,
    zone       TEXT,
    dist_m     NUMERIC(10, 2),
    temp_c     NUMERIC(6, 2),
    battery    INT,
    alert_type TEXT CHECK (alert_type IN ('critical', 'danger', 'warning', 'info')),
    alert_msg  TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_logs_created ON alert_logs (created_at DESC, id DESC);
CREATE INDEX idx_personnel_mac ON personnel (assigned_mac);
CREATE INDEX idx_tags_assigned ON tags (assigned_to);
