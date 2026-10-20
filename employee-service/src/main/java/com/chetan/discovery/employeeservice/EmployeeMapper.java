package com.chetan.discovery.employeeservice;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Implemented at compile time by MapStruct; registered as a Spring bean. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EmployeeMapper {
    EmployeeModel toModel(EmployeeEntity entity);
    EmployeeEntity toEntity(EmployeeModel model);
}
