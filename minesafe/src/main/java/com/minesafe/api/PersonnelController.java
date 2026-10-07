package com.minesafe.api;

import com.minesafe.domain.Personnel;
import com.minesafe.dto.PersonnelRequest;
import com.minesafe.service.PersonnelService;
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
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;

    public PersonnelController(PersonnelService personnelService) {
        this.personnelService = personnelService;
    }

    @GetMapping
    public List<Personnel> list() {
        return personnelService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Personnel save(@Valid @RequestBody PersonnelRequest request) {
        return personnelService.save(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        personnelService.delete(id);
    }

    @GetMapping("/by-mac/{mac}")
    public Personnel findByMac(@PathVariable String mac) {
        return personnelService.findByMac(mac);
    }
}
