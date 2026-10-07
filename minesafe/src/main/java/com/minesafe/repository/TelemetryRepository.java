package com.minesafe.repository;

import com.minesafe.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    /** Most recent readings, newest first — backs {@code GET /api/telemetry/recent}. */
    List<Telemetry> findFirst500ByOrderByCreatedAtDescIdDesc();

    /**
     * Deletes all telemetry rows older than the given cutoff. Used by the hourly
     * time-based retention job.
     *
     * @param cutoff keep rows with {@code created_at >= cutoff}; delete the rest
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM telemetry WHERE created_at < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
