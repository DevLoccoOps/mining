package com.minesafe.repository;

import com.minesafe.domain.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

    List<AlertLog> findFirst100ByOrderByCreatedAtDescIdDesc();

    /**
     * Deletes all alert_logs rows except the most recent {@code max} rows.
     * Used by the hourly retention cleanup job.
     *
     * @param max number of rows to retain
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM alert_logs WHERE id NOT IN "
            + "(SELECT id FROM alert_logs ORDER BY created_at DESC, id DESC LIMIT :max)",
            nativeQuery = true)
    int deleteExceedingRetention(@Param("max") int max);
}