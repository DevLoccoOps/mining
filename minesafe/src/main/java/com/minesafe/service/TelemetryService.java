package com.minesafe.service;

import com.minesafe.domain.Telemetry;
import com.minesafe.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Persists continuous telemetry readings and enforces a time-based retention
 * cap so the {@code telemetry} table does not grow without bound.
 *
 * <p>Mirrors {@link AlertLogService} for the write path and
 * {@link StateBroadcaster#enforceRetention()} for the scheduled cleanup.
 * Retention window is configured via {@code app.telemetry.retention-hours}
 * (default 24h, matching application.properties).
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryRepository repository;
    private final int retentionHours;

    public TelemetryService(TelemetryRepository repository,
                            @Value("${app.telemetry.retention-hours:24}") int retentionHours) {
        this.repository = repository;
        this.retentionHours = retentionHours;
    }

    @Transactional
    public Telemetry record(Telemetry telemetry) {
        return repository.save(telemetry);
    }

    /** Most recent readings, newest first (capped at 500 by the repository). */
    public List<Telemetry> recent() {
        return repository.findFirst500ByOrderByCreatedAtDescIdDesc();
    }

    /**
     * Hourly retention cleanup: deletes telemetry rows older than the
     * configured retention window. Failures are logged, never propagated.
     */
    @Scheduled(fixedRate = 3600000)
    public void enforceRetention() {
        try {
            Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Telemetry retention: deleted {} rows older than {}h", deleted, retentionHours);
            }
        } catch (Exception e) {
            log.warn("Telemetry retention cleanup failed: {}", e.getMessage());
        }
    }
}
