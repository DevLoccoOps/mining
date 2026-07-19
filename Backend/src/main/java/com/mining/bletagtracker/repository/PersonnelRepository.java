package com.mining.bletagtracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mining.bletagtracker.entity.Personnel;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {


    List<Personnel> findByActiveTrue();

}
