package com.chetan.discovery.collegeservice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Transaction boundary for database operations; constructor dependencies allow isolated tests. */
@Service
@Transactional(readOnly = true)
public class CollegeService {
    private final CollegeRepository repository;
    private final CollegeMapper mapper;
    public CollegeService(CollegeRepository repository, CollegeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    public CollegeModel find(long id) { return mapper.toModel(required(id)); }
    @Transactional
    public CollegeModel create(CollegeRequest request) {
        var entity = mapper.toEntity(new CollegeModel(null, request.name()));
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public CollegeModel update(long id, CollegeRequest request) {
        var entity = required(id);
        entity.setName(request.name());
        return mapper.toModel(repository.save(entity));
    }
    @Transactional
    public void delete(long id) { repository.delete(required(id)); }
    private CollegeEntity required(long id) {
        return repository.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }
}
