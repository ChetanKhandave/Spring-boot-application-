package com.chetan.discovery.collegeservice;

import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolates service decisions from database infrastructure, using the real MapStruct mapper. */
@ExtendWith(MockitoExtension.class)
class CollegeServiceTest {
    @Mock CollegeRepository repository;
    private CollegeService service;
    private final CollegeMapper mapper = Mappers.getMapper(CollegeMapper.class);
    @BeforeEach void setup() { service = new CollegeService(repository, mapper); }
    @Test void findsExistingRecord() {
        when(repository.findById(7L)).thenReturn(Optional.of(mapper.toEntity(new CollegeModel(7L, "Name"))));
        assertEquals(new CollegeModel(7L, "Name"), service.find(7L));
    }
    @Test void missingRecordsReturn404WithoutWrites() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.find(7L)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.update(7L, new CollegeRequest("Name")));
        assertThrows(ResponseStatusException.class, () -> service.delete(7L));
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any(CollegeEntity.class));
    }
    @Test void createsWithGeneratedIdentifier() {
        when(repository.save(any())).thenAnswer(invocation -> {
            CollegeEntity entity = invocation.getArgument(0);
            assertNull(entity.getId()); entity.setId(1000L); return entity;
        });
        assertEquals(new CollegeModel(1000L, "Created"), service.create(new CollegeRequest("Created")));
    }
    @Test void updatesAndDeletesExistingRecord() {
        var entity = mapper.toEntity(new CollegeModel(7L, "Before"));
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        assertEquals(new CollegeModel(7L, "After"), service.update(7L, new CollegeRequest("After")));
        service.delete(7L);
        verify(repository).delete(entity);
    }
}
