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
        // Skip duplicate if the same signal was already recorded (same serial number + timestamp)
        List<SignalReading> existing = signalRepository.findBySerialNumberAndTimestamp(
                request.serialNumber(), request.timestamp());
        if (!existing.isEmpty()) {
            return null;
        }

        SignalReading reading = SignalReading.builder()
                .serialNumber(request.serialNumber())
                .rssi(request.rssi())
                .timestamp(request.timestamp())
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
            signalRepository.deleteAllById(idsToDelete);
        }
    }

    public List<SignalReading> getSignalsForTag(String serialNumber) {
        return signalRepository.findFirst50BySerialNumberOrderByTimestampAsc(serialNumber);
    }
}
