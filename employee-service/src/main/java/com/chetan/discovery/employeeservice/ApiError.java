package com.chetan.discovery.employeeservice;

/** Safe public error contract. errorId correlates a response with diagnostic logs. */
public record ApiError(String timestamp, int status, String error, String code,
                       String message, String path, String errorId) {}
