package com.chetan.discovery.employeeservice;

/** Immutable application model, independent of JPA entity lifecycle. */
public record EmployeeModel(Long id, String name) {}
