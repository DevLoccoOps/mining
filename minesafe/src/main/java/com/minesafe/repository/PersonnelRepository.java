package com.minesafe.repository;

import com.minesafe.domain.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PersonnelRepository extends JpaRepository<Personnel, String> {

    Optional<Personnel> findByAssignedMac(String assignedMac);

    @Modifying(clearAutomatically = true)
    @Query("update Personnel p set p.assignedMac = :mac where p.id = :personnelId")
    int assignMac(@Param("personnelId") String personnelId, @Param("mac") String mac);

    @Modifying(clearAutomatically = true)
    @Query("update Personnel p set p.assignedMac = null where p.assignedMac = :mac")
    int clearMac(@Param("mac") String mac);
}
