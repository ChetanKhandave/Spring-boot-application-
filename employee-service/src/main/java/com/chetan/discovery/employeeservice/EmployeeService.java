package com.chetan.discovery.employeeservice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Transaction boundary for database operations; constructor dependencies allow isolated tests. */
@Service
@Transactional(readOnly = true)
public class EmployeeService {
    private final EmployeeRepository repository;
    private final EmployeeMapper mapper;
    public EmployeeService(EmployeeRepository repository, EmployeeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    public EmployeeModel find(long id) { return mapper.toModel(required(id)); }
    @Transactional
    public EmployeeModel create(EmployeeRequest request) {
        var entity = mapper.toEntity(new EmployeeModel(null, request.name()));
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public EmployeeModel update(long id, EmployeeRequest request) {
        var entity = required(id);
        entity.setName(request.name());
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public void delete(long id) { repository.delete(required(id)); }
    private EmployeeEntity required(long id) {
        return repository.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }
}
