package com.mining.bletagtracker.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mining.bletagtracker.dto.PersonnelRequest;
import com.mining.bletagtracker.entity.Personnel;
import com.mining.bletagtracker.service.PersonnelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/personnel")
@RequiredArgsConstructor

public class PersonnelController {


    private final PersonnelService service;



    // CREATE PERSONNEL

    @PostMapping
    public ResponseEntity<Personnel> create(
            @RequestBody PersonnelRequest request
    ){

        return ResponseEntity.ok(
                service.createPersonnel(request)
        );

    }



    // GET ALL ACTIVE PERSONNEL

    @GetMapping
    public ResponseEntity<List<Personnel>> getAll(){

        return ResponseEntity.ok(
                service.getAllPersonnel()
        );

    }



    // GET PERSON BY ID

    @GetMapping("/{id}")
    public ResponseEntity<Personnel> getById(
            @PathVariable Long id
    ){

        return ResponseEntity.ok(
                service.getPersonnel(id)
        );

    }



    // UPDATE PERSONNEL

    @PutMapping("/{id}")
    public ResponseEntity<Personnel> update(
            @PathVariable Long id,
            @RequestBody PersonnelRequest request
    ){

        return ResponseEntity.ok(
                service.updatePersonnel(id, request)
        );

    }



    // SOFT DELETE

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id
    ){

        service.deletePersonnel(id);

        return ResponseEntity.ok(
                "Personnel deactivated successfully"
        );

    }



    // ASSIGN TAG TO PERSON

    @PutMapping("/{personnelId}/tag/{tagSerial}")
    public ResponseEntity<Personnel> assignTag(
            @PathVariable Long personnelId,
            @PathVariable String tagSerial
    ){

        return ResponseEntity.ok(
                service.assignTag(personnelId, tagSerial)
        );

    }



    // REMOVE TAG FROM PERSON

    @DeleteMapping("/{personnelId}/tag")
    public ResponseEntity<String> removeTag(
            @PathVariable Long personnelId
    ){

        service.removeTag(personnelId);

        return ResponseEntity.ok(
                "Tag removed successfully"
        );

    }


}