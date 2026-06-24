package com.mining.bletagtracker.repository;

import com.mining.bletagtracker.entity.SignalReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalReadingRepository extends JpaRepository<SignalReading, Long> {

    List<SignalReading> findBySerialNumberOrderByTimestampAsc(String serialNumber);

    List<SignalReading> findTop5BySerialNumberOrderByTimestampDesc(String serialNumber);

    List<SignalReading> findBySerialNumberAndTimestamp(String serialNumber, java.time.LocalDateTime timestamp);

    long countBySerialNumber(String serialNumber);

    List<SignalReading> findFirst50BySerialNumberOrderByTimestampAsc(String serialNumber);
}
