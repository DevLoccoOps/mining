package com.minesafe.service;

import com.minesafe.domain.AlertLog;
import com.minesafe.repository.AlertLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertLogService {

    private final AlertLogRepository alertLogRepository;

    public AlertLogService(AlertLogRepository alertLogRepository) {
        this.alertLogRepository = alertLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertLog> recent() {
        return alertLogRepository.findFirst100ByOrderByCreatedAtDescIdDesc();
    }

    @Transactional
    public AlertLog record(AlertLog alertLog) {
        return alertLogRepository.save(alertLog);
    }
}
