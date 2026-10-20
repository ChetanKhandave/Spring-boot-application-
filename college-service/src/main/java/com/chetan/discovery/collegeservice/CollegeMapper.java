package com.chetan.discovery.collegeservice;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Implemented at compile time by MapStruct; registered as a Spring bean. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CollegeMapper {
    CollegeModel toModel(CollegeEntity entity);
    CollegeEntity toEntity(CollegeModel model);
}
