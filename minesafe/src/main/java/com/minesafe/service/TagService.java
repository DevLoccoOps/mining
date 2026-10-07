package com.minesafe.service;

import com.minesafe.domain.Personnel;
import com.minesafe.domain.Tag;
import com.minesafe.dto.TagRequest;
import com.minesafe.repository.PersonnelRepository;
import com.minesafe.repository.TagRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TagService {

    private static final Pattern MAC_PATTERN = Pattern.compile("[0-9A-F]{12}");
    private static final Set<String> TYPES = Set.of(Tag.TYPE_INDOOR, Tag.TYPE_OUTDOOR);

    private final TagRepository tagRepository;
    private final PersonnelRepository personnelRepository;

    public TagService(TagRepository tagRepository, PersonnelRepository personnelRepository) {
        this.tagRepository = tagRepository;
        this.personnelRepository = personnelRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> list() {
        return tagRepository.findAll(Sort.by("mac").ascending());
    }

    /** Optional tag lookup by MAC for the ingestion pipeline. Returns empty for invalid MAC formats. */
    @Transactional(readOnly = true)
    public Optional<Tag> findByMacOrNull(String mac) {
        try {
            String normalized = normalizeMac(mac);
            return tagRepository.findById(normalized);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Creates or updates a tag. When {@code action=assign} the tag is bound to the
     * given personnel and any other holder of the same MAC is cleared; when
     * {@code action=unassign} the binding is released. Both mutate personnel and
     * tags atomically in a single transaction (replacing the legacy two-file dance).
     */
    @Transactional
    public Tag save(TagRequest request) {
        String mac = normalizeMac(request.mac());

        Tag tag = tagRepository.findById(mac)
                .orElseGet(() -> new Tag(mac, null));

        String action = request.action() == null ? "update" : request.action().trim().toLowerCase();
        switch (action) {
            case "assign" -> {
                String holder = requirePersonnel(request.assignedTo());
                personnelRepository.clearMac(mac);
                personnelRepository.assignMac(holder, mac);
                tag.setAssignedTo(holder);
            }
            case "unassign" -> {
                personnelRepository.clearMac(mac);
                tag.setAssignedTo(null);
            }
            default -> {
                if (request.type() != null) {
                    tag.setType(normalizeType(request.type()));
                }
            }
        }

        if (tag.getType() == null) {
            tag.setType(Tag.TYPE_OUTDOOR);
        }
        return tagRepository.save(tag);
    }

    @Transactional
    public void delete(String mac) {
        String normalized = normalizeMac(mac);
        if (!tagRepository.existsById(normalized)) {
            throw new NoSuchElementException("Tag not found: " + normalized);
        }
        personnelRepository.clearMac(normalized);
        tagRepository.deleteById(normalized);
    }

    private String requirePersonnel(String personnelId) {
        if (personnelId == null || personnelId.isBlank()) {
            throw new IllegalArgumentException("assigned_to is required for action=assign");
        }
        Personnel personnel = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new NoSuchElementException("Personnel not found: " + personnelId));
        return personnel.getId();
    }

    static String normalizeMac(String raw) {
        String mac = raw.trim().toUpperCase().replace(":", "").replace("-", "");
        if (!MAC_PATTERN.matcher(mac).matches()) {
            throw new IllegalArgumentException("Invalid MAC address: " + raw);
        }
        return mac;
    }

    static String normalizeType(String raw) {
        String type = raw.trim().toLowerCase();
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Tag type must be 'indoor' or 'outdoor', got: " + raw);
        }
        return type;
    }
}
