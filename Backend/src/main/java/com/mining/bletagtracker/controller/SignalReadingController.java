package com.mining.bletagtracker.controller;

import com.mining.bletagtracker.dto.CreateSignalRequest;
import com.mining.bletagtracker.entity.SignalReading;
import com.mining.bletagtracker.service.SignalReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.mining.bletagtracker.handler.SignalWebSocketHandler;
import com.mining.bletagtracker.repository.SignalReadingRepository;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalReadingController {

    private final SignalReadingService signalService;
    private final SignalReadingRepository signalRepository;
    private final SignalWebSocketHandler webSocketHandler;

    @PostMapping
    public ResponseEntity<Void> recordSignal(@Valid @RequestBody CreateSignalRequest request) {
        SignalReading saved = signalService.recordSignal(request);
        // Broadcast real-time to connected WebSocket clients (only if not duplicate)
        if (saved != null) {
            webSocketHandler.broadcastSignal(saved);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/tags/{serialNumber}")
    public ResponseEntity<List<SignalReading>> getSignals(@PathVariable String serialNumber) {
        return ResponseEntity.ok(signalService.getSignalsForTag(serialNumber));
    }

    @GetMapping("/tags/{serialNumber}/latest")
    public ResponseEntity<SignalReading> getLatestSignal(@PathVariable String serialNumber) {
        List<SignalReading> latest = signalRepository.findTop5BySerialNumberOrderByTimestampDesc(serialNumber);
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest.get(0));
    }
}
