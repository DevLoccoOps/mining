package com.minesafe.api;

import com.minesafe.domain.Telemetry;
import com.minesafe.service.TelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /** Most recent continuous readings, newest first (capped at 500). */
    @GetMapping("/recent")
    public List<Telemetry> recent() {
        return telemetryService.recent();
    }
}