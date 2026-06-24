package com.mining.bletagtracker.repository;

import com.mining.bletagtracker.entity.BleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BleTagRepository extends JpaRepository<BleTag, String> {

    List<BleTag> findAllByOrderByCreatedAtDesc();
}
