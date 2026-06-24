package com.mining.bletagtracker.service;

import com.mining.bletagtracker.dto.CreateSignalRequest;
import com.mining.bletagtracker.entity.SignalReading;
import com.mining.bletagtracker.repository.SignalReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalReadingService {

    private static final int MAX_SIGNALS_PER_TAG = 50;

    private final SignalReadingRepository signalRepository;

    public SignalReading recordSignal(CreateSignalRequest request) {
        // Truncate to microseconds — matches PostgreSQL precision, so the check
        // actually finds what was stored (nanoseconds would never match).
        java.time.LocalDateTime ts = request.timestamp().withNano((request.timestamp().getNano() / 1_000) * 1_000);

        // Skip duplicate if the same signal was already recorded (same serial number + timestamp)
        List<SignalReading> existing = signalRepository.findBySerialNumberAndTimestamp(
                request.serialNumber(), ts);
        if (!existing.isEmpty()) {
            return null;
        }

        SignalReading reading = SignalReading.builder()
                .serialNumber(request.serialNumber())
                .rssi(request.rssi())
                .timestamp(ts)
                .build();
        SignalReading saved = signalRepository.save(reading);

        // FIFO: keep only the most recent MAX_SIGNALS_PER_TAG readings per tag
        pruneOldSignals(request.serialNumber());

        return saved;
    }

    private void pruneOldSignals(String serialNumber) {
        long count = signalRepository.countBySerialNumber(serialNumber);
        if (count > MAX_SIGNALS_PER_TAG) {
            List<SignalReading> all = signalRepository.findBySerialNumberOrderByTimestampAsc(serialNumber);
            int toDelete = all.size() - MAX_SIGNALS_PER_TAG;
            List<Long> idsToDelete = all.stream().limit(toDelete).map(SignalReading::getId).toList();
            try {
                signalRepository.deleteAllById(idsToDelete);
            } catch (Exception e) {
                // Ignore race condition — another thread may have already deleted some IDs
            }
        }
    }

    public List<SignalReading> getSignalsForTag(String serialNumber) {
        return signalRepository.findFirst50BySerialNumberOrderByTimestampAsc(serialNumber);
    }
}
