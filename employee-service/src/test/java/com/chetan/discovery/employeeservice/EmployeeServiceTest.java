package com.chetan.discovery.employeeservice;

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
class EmployeeServiceTest {
    @Mock EmployeeRepository repository;
    private EmployeeService service;
    private final EmployeeMapper mapper = Mappers.getMapper(EmployeeMapper.class);
    @BeforeEach void setup() { service = new EmployeeService(repository, mapper); }
    @Test void findsExistingRecord() {
        when(repository.findById(7L)).thenReturn(Optional.of(mapper.toEntity(new EmployeeModel(7L, "Name"))));
        assertEquals(new EmployeeModel(7L, "Name"), service.find(7L));
    }
    @Test void missingRecordsReturn404WithoutWrites() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.find(7L)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.update(7L, new EmployeeRequest("Name")));
        assertThrows(ResponseStatusException.class, () -> service.delete(7L));
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any(EmployeeEntity.class));
    }
    @Test void createsWithGeneratedIdentifier() {
        when(repository.save(any())).thenAnswer(invocation -> {
            EmployeeEntity entity = invocation.getArgument(0);
            assertNull(entity.getId()); entity.setId(1000L); return entity;
        });
        assertEquals(new EmployeeModel(1000L, "Created"), service.create(new EmployeeRequest("Created")));
    }
    @Test void updatesAndDeletesExistingRecord() {
        var entity = mapper.toEntity(new EmployeeModel(7L, "Before"));
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        assertEquals(new EmployeeModel(7L, "After"), service.update(7L, new EmployeeRequest("After")));
        service.delete(7L);
        verify(repository).delete(entity);
    }
}
