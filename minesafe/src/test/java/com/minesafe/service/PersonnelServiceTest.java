package com.minesafe.service;

import com.minesafe.domain.Personnel;
import com.minesafe.dto.PersonnelRequest;
import com.minesafe.repository.PersonnelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PersonnelServiceTest {

    @Mock
    private PersonnelRepository personnelRepository;

    private PersonnelService personnelService;

    @BeforeEach
    void setUp() {
        personnelService = new PersonnelService(personnelRepository);
    }

    @Test
    void normalizeMacMatchesTagRegistryFormat() {
        // Colons and dashes must be stripped so a MAC entered by an operator
        // matches the colon-less form the ingestion pipeline looks up.
        assertEquals("AABBCCDDEEFF", PersonnelService.normalizeMac("aa:bb:cc:dd:ee:ff"));
        assertEquals("AABBCCDDEEFF", PersonnelService.normalizeMac("AABB-CCDD-EEFF"));
        assertThrows(IllegalArgumentException.class, () -> PersonnelService.normalizeMac("nope"));
    }

    @Test
    void saveNormalizesAssignedMacToBareUppercase() {
        personnelService.save(new PersonnelRequest(null, "Jane Miner", "Miner", "A", "aa:bb:cc:dd:ee:ff"));

        ArgumentCaptor<Personnel> captor = ArgumentCaptor.forClass(Personnel.class);
        verify(personnelRepository).save(captor.capture());
        assertEquals("AABBCCDDEEFF", captor.getValue().getAssignedMac());
    }

    @Test
    void saveTreatsBlankMacAsUnassigned() {
        personnelService.save(new PersonnelRequest(null, "Jane Miner", "Miner", "A", "  "));

        ArgumentCaptor<Personnel> captor = ArgumentCaptor.forClass(Personnel.class);
        verify(personnelRepository).save(captor.capture());
        assertNull(captor.getValue().getAssignedMac());
    }

    @Test
    void saveRejectsMalformedMac() {
        assertThrows(IllegalArgumentException.class, () ->
                personnelService.save(new PersonnelRequest(null, "Jane Miner", "Miner", "A", "ZZ:ZZ:ZZ")));
        verify(personnelRepository, org.mockito.Mockito.never()).save(any(Personnel.class));
    }

    @Test
    void findByMacOrNullSwallowsInvalidMac() {
        assertEquals(Optional.empty(), personnelService.findByMacOrNull("garbage"));
    }
}