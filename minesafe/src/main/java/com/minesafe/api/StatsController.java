package com.minesafe.api;

import com.minesafe.repository.AlertLogRepository;
import com.minesafe.repository.TelemetryRepository;
import com.minesafe.service.GatewayStateStore;
import com.minesafe.service.MinerStateStore;
import com.minesafe.service.PersonnelService;
import com.minesafe.service.TagService;
import com.minesafe.ws.LiveHub;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;

/**
 * System-level snapshot for the frontend's health/status pages: uptime,
 * in-memory live counts (miners, gateways, WebSocket clients) and table
 * sizes. Cheap enough to poll every few seconds.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final MinerStateStore minerStateStore;
    private final GatewayStateStore gatewayStateStore;
    private final LiveHub liveHub;
    private final PersonnelService personnelService;
    private final TagService tagService;
    private final AlertLogRepository alertLogRepository;
    private final TelemetryRepository telemetryRepository;
    private final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

    public StatsController(MinerStateStore minerStateStore,
                           GatewayStateStore gatewayStateStore,
                           LiveHub liveHub,
                           PersonnelService personnelService,
                           TagService tagService,
                           AlertLogRepository alertLogRepository,
                           TelemetryRepository telemetryRepository) {
        this.minerStateStore = minerStateStore;
        this.gatewayStateStore = gatewayStateStore;
        this.liveHub = liveHub;
        this.personnelService = personnelService;
        this.tagService = tagService;
        this.alertLogRepository = alertLogRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @GetMapping
    public StatsSnapshot stats() {
        return new StatsSnapshot(
                runtime.getUptime() / 1000.0,
                Instant.now().toEpochMilli() / 1000.0,
                minerStateStore.all().size(),
                gatewayStateStore.activeCount(),
                liveHub.activeSessions(),
                personnelService.list().size(),
                tagService.list().size(),
                alertLogRepository.count(),
                telemetryRepository.count());
    }

    // Serialized as snake_case by the global Jackson naming strategy.
    public record StatsSnapshot(double uptimeSeconds, double serverTime, int liveMiners,
                                int activeGateways, int wsClients, int personnelCount,
                                int tagCount, long alertLogCount, long telemetryCount) {}
}