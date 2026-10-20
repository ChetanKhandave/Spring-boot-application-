package com.chetan.discovery.studentservice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Transaction boundary for database operations; constructor dependencies allow isolated tests. */
@Service
@Transactional(readOnly = true)
public class StudentService {
    private final StudentRepository repository;
    private final StudentMapper mapper;
    public StudentService(StudentRepository repository, StudentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    public StudentModel find(long id) { return mapper.toModel(required(id)); }
    @Transactional
    public StudentModel create(StudentRequest request) {
        var entity = mapper.toEntity(new StudentModel(null, request.name()));
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public StudentModel update(long id, StudentRequest request) {
        var entity = required(id);
        entity.setName(request.name());
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public void delete(long id) { repository.delete(required(id)); }
    private StudentEntity required(long id) {
        return repository.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }
}
