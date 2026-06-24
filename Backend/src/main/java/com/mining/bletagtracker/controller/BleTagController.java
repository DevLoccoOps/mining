package com.mining.bletagtracker.controller;

import com.mining.bletagtracker.dto.CreateTagRequest;
import com.mining.bletagtracker.dto.ScannerTagResponse;
import com.mining.bletagtracker.dto.TagResponse;
import com.mining.bletagtracker.service.BleTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class BleTagController {

    private final BleTagService tagService;

    @PostMapping
    public ResponseEntity<TagResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(request));
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> listTags() {
        return ResponseEntity.ok(tagService.listTags());
    }

    @GetMapping("/{serialNumber}")
    public ResponseEntity<TagResponse> getTag(@PathVariable String serialNumber) {
        return ResponseEntity.ok(tagService.getTag(serialNumber));
    }

    @DeleteMapping("/{serialNumber}")
    public ResponseEntity<Void> deleteTag(@PathVariable String serialNumber) {
        tagService.deleteTag(serialNumber);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scanner")
    public ResponseEntity<List<ScannerTagResponse>> scannerTags() {
        return ResponseEntity.ok(tagService.getTagsForScanner());
    }
}
