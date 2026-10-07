package com.minesafe.api;

import com.minesafe.domain.Tag;
import com.minesafe.dto.TagRequest;
import com.minesafe.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<Tag> list() {
        return tagService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tag save(@Valid @RequestBody TagRequest request) {
        return tagService.save(request);
    }

    @DeleteMapping("/{mac}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String mac) {
        tagService.delete(mac);
    }
}
