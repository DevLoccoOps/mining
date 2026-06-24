package com.mining.bletagtracker.service;

import com.mining.bletagtracker.dto.CreateTagRequest;
import com.mining.bletagtracker.dto.ScannerTagResponse;
import com.mining.bletagtracker.dto.TagResponse;
import com.mining.bletagtracker.entity.BleTag;
import com.mining.bletagtracker.repository.BleTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BleTagService {

    private static final java.time.Duration CACHE_TTL = java.time.Duration.ofSeconds(30);

    private final BleTagRepository tagRepository;

    public TagResponse createTag(CreateTagRequest request) {
        if (tagRepository.existsById(request.serialNumber())) {
            throw new IllegalArgumentException("Tag with serial number " + request.serialNumber() + " already exists");
        }
        BleTag tag = BleTag.builder()
                .serialNumber(request.serialNumber())
                .name(request.name())
                .description(request.description())
                .location(request.location())
                .ibeaconUuids(request.ibeaconUuids())
                .serviceUuids(request.serviceUuids())
                .mfgSignatures(request.mfgSignatures())
                .build();
        BleTag saved = tagRepository.save(tag);
        return toResponse(saved);
    }

    public List<TagResponse> listTags() {
        return tagRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public TagResponse getTag(String serialNumber) {
        BleTag tag = tagRepository.findById(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + serialNumber));
        return toResponse(tag);
    }

    public void deleteTag(String serialNumber) {
        if (!tagRepository.existsById(serialNumber)) {
            throw new IllegalArgumentException("Tag not found: " + serialNumber);
        }
        tagRepository.deleteById(serialNumber);
    }

    public List<ScannerTagResponse> getTagsForScanner() {
        return tagRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toScannerResponse)
                .toList();
    }

    private ScannerTagResponse toScannerResponse(BleTag tag) {
        return new ScannerTagResponse(
                tag.getSerialNumber(),
                tag.getName() != null ? tag.getName() : tag.getSerialNumber(),
                tag.getIbeaconUuids(),
                tag.getServiceUuids(),
                tag.getMfgSignatures(),
                -90 // default RSSI threshold for scanner
        );
    }

    private TagResponse toResponse(BleTag tag) {
        return new TagResponse(
                tag.getSerialNumber(),
                tag.getName(),
                tag.getDescription(),
                tag.getLocation(),
                tag.getCreatedAt(),
                tag.getUpdatedAt(),
                tag.getIbeaconUuids(),
                tag.getServiceUuids(),
                tag.getMfgSignatures()
        );
    }
}
