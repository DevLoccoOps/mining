package com.minesafe.service;

import com.minesafe.domain.Personnel;
import com.minesafe.dto.PersonnelRequest;
import com.minesafe.repository.PersonnelRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PersonnelService {

    private static final Pattern MAC_PATTERN = Pattern.compile("[0-9A-F]{12}");

    private final PersonnelRepository personnelRepository;

    public PersonnelService(PersonnelRepository personnelRepository) {
        this.personnelRepository = personnelRepository;
    }

    @Transactional(readOnly = true)
    public List<Personnel> list() {
        return personnelRepository.findAll(Sort.by("name").ascending());
    }

    @Transactional(readOnly = true)
    public Personnel findByMac(String mac) {
        String normalized = normalizeMac(mac);
        return personnelRepository.findByAssignedMac(normalized)
                .orElseThrow(() -> new NoSuchElementException("No personnel assigned to MAC: " + mac));
    }

    /** Optional variant of {@link #findByMac} for the ingestion pipeline. Returns empty for invalid MAC formats. */
    @Transactional(readOnly = true)
    public Optional<Personnel> findByMacOrNull(String mac) {
        try {
            return personnelRepository.findByAssignedMac(normalizeMac(mac));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Personnel save(PersonnelRequest request) {
        Personnel personnel;
        if (request.id() != null && !request.id().isBlank()
                && personnelRepository.existsById(request.id())) {
            personnel = personnelRepository.findById(request.id())
                    .orElseThrow(() -> new NoSuchElementException("Personnel not found: " + request.id()));
        } else {
            personnel = new Personnel(newPersonnelId(), null, null, null);
        }

        personnel.setName(request.name().trim());
        if (request.role() != null) {
            personnel.setRole(request.role().trim());
        }
        if (request.shift() != null) {
            personnel.setShift(request.shift().trim());
        }
        if (request.assignedMac() != null) {
            personnel.setAssignedMac(
                    request.assignedMac().isBlank() ? null : normalizeMac(request.assignedMac()));
        }
        return personnelRepository.save(personnel);
    }

    @Transactional
    public void delete(String id) {
        if (!personnelRepository.existsById(id)) {
            throw new NoSuchElementException("Personnel not found: " + id);
        }
        personnelRepository.deleteById(id);
    }

    /** 8-char uppercase id, matching the legacy Python generator (uuid4 first 8 hex chars). */
    static String newPersonnelId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * Normalizes a MAC to the bare 12-hex-digit uppercase form used everywhere
     * else (tag registry, ingestion lookups), so a MAC entered with colons or
     * dashes still matches incoming telemetry.
     */
    static String normalizeMac(String raw) {
        String mac = raw.trim().toUpperCase().replace(":", "").replace("-", "");
        if (!MAC_PATTERN.matcher(mac).matches()) {
            throw new IllegalArgumentException("Invalid MAC address: " + raw);
        }
        return mac;
    }
}
