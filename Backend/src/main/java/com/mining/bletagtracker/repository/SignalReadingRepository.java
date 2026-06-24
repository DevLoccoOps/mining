package com.mining.bletagtracker.repository;

import com.mining.bletagtracker.entity.SignalReading;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SignalReadingRepository extends JpaRepository<SignalReading, Long> {

    List<SignalReading> findBySerialNumberOrderByTimestampAsc(String serialNumber);

    List<SignalReading> findTop5BySerialNumberOrderByTimestampDesc(String serialNumber);

    List<SignalReading> findBySerialNumberAndTimestamp(String serialNumber, java.time.LocalDateTime timestamp);

    long countBySerialNumber(String serialNumber);

    List<SignalReading> findFirst50BySerialNumberOrderByTimestampAsc(String serialNumber);

    @Modifying
    @Query(value = "DELETE FROM signal_readings WHERE id IN (" +
            "SELECT id FROM signal_readings WHERE serial_number = :serialNumber " +
            "ORDER BY timestamp ASC LIMIT 1000)", nativeQuery = true)
    int deleteOldestExcessBySerialNumber(String serialNumber);
}
