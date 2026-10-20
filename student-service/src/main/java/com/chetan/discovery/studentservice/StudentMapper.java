package com.chetan.discovery.studentservice;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Implemented at compile time by MapStruct; registered as a Spring bean. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StudentMapper {
    StudentModel toModel(StudentEntity entity);
    StudentEntity toEntity(StudentModel model);
}
