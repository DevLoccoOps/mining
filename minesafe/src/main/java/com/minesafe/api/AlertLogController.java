package com.minesafe.api;

import com.minesafe.domain.AlertLog;
import com.minesafe.service.AlertLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class AlertLogController {

    private final AlertLogService alertLogService;

    public AlertLogController(AlertLogService alertLogService) {
        this.alertLogService = alertLogService;
    }

    @GetMapping
    public List<AlertLog> recent() {
        return alertLogService.recent();
    }
}
