package com.mining.bletagtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.mining.bletagtracker.dto.PersonnelRequest;
import com.mining.bletagtracker.entity.BleTag;
import com.mining.bletagtracker.entity.Personnel;
import com.mining.bletagtracker.repository.BleTagRepository;
import com.mining.bletagtracker.repository.PersonnelRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonnelService {

        private final PersonnelRepository personnelRepository;

        private final BleTagRepository bleTagRepository;

        // CREATE PERSONNEL

        public Personnel createPersonnel(PersonnelRequest request) {

                Personnel personnel = Personnel.builder()
                                .employeeNumber(request.getEmployeeNumber())
                                .idNumber(request.getIdNumber())
                                .firstName(request.getFirstName())
                                .surname(request.getSurname())
                                .phoneNumber(request.getPhoneNumber())
                                .department(request.getDepartment())
                                .positionRole(request.getPositionRole())
                                .contractor(request.getContractor())
                                .emergencyContactName(request.getEmergencyContactName())
                                .emergencyRelationship(request.getEmergencyRelationship())
                                .emergencyPhone(request.getEmergencyPhone())
                                .active(true)
                                .build();

                return personnelRepository.save(personnel);

        }

        // GET ALL ACTIVE PERSONNEL

        public List<Personnel> getAllPersonnel() {

                return personnelRepository.findByActiveTrue();

        }

        // GET PERSONNEL BY ID

        public Personnel getPersonnel(Long id) {

                return personnelRepository.findById(id)

                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Personnel not found"));

        }

        // UPDATE PERSONNEL

        public Personnel updatePersonnel(
                        Long id,
                        PersonnelRequest request) {

                Personnel existing = getPersonnel(id);

                existing.setEmployeeNumber(
                                request.getEmployeeNumber());

                existing.setIdNumber(
                                request.getIdNumber());

                existing.setFirstName(
                                request.getFirstName());

                existing.setSurname(
                                request.getSurname());

                existing.setPhoneNumber(
                                request.getPhoneNumber());

                existing.setDepartment(
                                request.getDepartment());

                existing.setPositionRole(
                                request.getPositionRole());

                existing.setContractor(
                                request.getContractor());
                existing.setEmergencyContactName(request.getEmergencyContactName());
                existing.setEmergencyRelationship(request.getEmergencyRelationship());
                existing.setEmergencyPhone(request.getEmergencyPhone());

                return personnelRepository.save(existing);

        }

        // SOFT DELETE

        public void deletePersonnel(Long id) {

                Personnel personnel = getPersonnel(id);

                personnel.setActive(false);

                personnelRepository.save(personnel);

        }

        // ASSIGN BLE TAG TO PERSON

        public Personnel assignTag(
                        Long personnelId,
                        String tagSerial) {

                Personnel personnel = getPersonnel(personnelId);

                BleTag tag = bleTagRepository.findById(tagSerial)

                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "BLE Tag not found"));

                personnel.setBleTag(tag);

                return personnelRepository.save(personnel);

        }

        // REMOVE BLE TAG

        public void removeTag(Long personnelId) {

                Personnel personnel = getPersonnel(personnelId);

                personnel.setBleTag(null);

                personnelRepository.save(personnel);

        }

}