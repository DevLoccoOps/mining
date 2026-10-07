package com.minesafe.repository;

import com.minesafe.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String> {

    Optional<Tag> findByAssignedTo(String assignedTo);
}
