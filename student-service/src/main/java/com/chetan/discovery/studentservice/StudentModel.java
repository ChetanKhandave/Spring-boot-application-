package com.chetan.discovery.studentservice;

/** Immutable application model, independent of JPA entity lifecycle. */
public record StudentModel(Long id, String name) {}
