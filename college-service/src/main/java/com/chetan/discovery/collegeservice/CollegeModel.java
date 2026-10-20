package com.chetan.discovery.collegeservice;

/** Immutable application model, independent of JPA entity lifecycle. */
public record CollegeModel(Long id, String name) {}
