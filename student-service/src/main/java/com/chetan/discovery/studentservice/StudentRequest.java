package com.chetan.discovery.studentservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Write payload: database identifiers are assigned by the server. */
public record StudentRequest(@NotBlank @Size(max = 100) String name) {}
