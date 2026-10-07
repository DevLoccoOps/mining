package com.minesafe.service;

import com.minesafe.domain.Personnel;
import com.minesafe.domain.Tag;
import com.minesafe.dto.TagRequest;
import com.minesafe.repository.PersonnelRepository;
import com.minesafe.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private PersonnelRepository personnelRepository;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, personnelRepository);
    }

    @Test
    void normalizeMacStripsSeparatorsAndUppercases() {
        assertEquals("AABBCCDDEEFF", TagService.normalizeMac("aa:bb:cc:dd:ee:ff"));
        assertEquals("AABBCCDDEEFF", TagService.normalizeMac("AABB-CCDD-EEFF"));
        assertEquals("AABBCCDDEEFF", TagService.normalizeMac(" aabbccddeeff "));
    }

    @Test
    void normalizeMacRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> TagService.normalizeMac("not-a-mac"));
        assertThrows(IllegalArgumentException.class, () -> TagService.normalizeMac("AABBCCDDEEF"));
    }

    @Test
    void invalidLookupMacYieldsEmptyInsteadOfThrowing() {
        assertEquals(Optional.empty(), tagService.findByMacOrNull("garbage"));
        verify(tagRepository, never()).findById(anyString());
    }

    @Test
    void assignBindsTagToPersonnel() {
        when(tagRepository.findById("AABBCCDDEEFF")).thenReturn(Optional.of(new Tag("AABBCCDDEEFF", "indoor")));
        when(personnelRepository.findById("EMP0001"))
                .thenReturn(Optional.of(new Personnel("EMP0001", "Jane Miner", "Miner", "A")));
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Tag result = tagService.save(new TagRequest("AA:BB:CC:DD:EE:FF", null, "assign", "EMP0001"));

        verify(personnelRepository).clearMac("AABBCCDDEEFF");
        verify(personnelRepository).assignMac("EMP0001", "AABBCCDDEEFF");
        assertEquals("EMP0001", result.getAssignedTo());
        assertEquals("indoor", result.getType());
    }

    @Test
    void unassignReleasesBinding() {
        Tag tag = new Tag("AABBCCDDEEFF", "outdoor");
        tag.setAssignedTo("EMP0001");
        when(tagRepository.findById("AABBCCDDEEFF")).thenReturn(Optional.of(tag));
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Tag result = tagService.save(new TagRequest("AABBCCDDEEFF", null, "unassign", null));

        verify(personnelRepository).clearMac("AABBCCDDEEFF");
        assertNull(result.getAssignedTo());
    }

    @Test
    void newTagDefaultsToOutdoorType() {
        when(tagRepository.findById("AABBCCDDEEFF")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Tag result = tagService.save(new TagRequest("AABBCCDDEEFF", null, null, null));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertEquals("outdoor", captor.getValue().getType());
        assertEquals("outdoor", result.getType());
    }

    @Test
    void deleteUnknownMacThrowsNotFound() {
        when(tagRepository.existsById("AABBCCDDEEFF")).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> tagService.delete("AABBCCDDEEFF"));
        verify(tagRepository, never()).deleteById(anyString());
    }
}