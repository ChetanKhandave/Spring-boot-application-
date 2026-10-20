package com.chetan.discovery.employeeservice;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;

/** HTTP adapter. Entities remain behind the service boundary. */
@RestController
@RequestMapping("/employees")
public class EmployeeController {
    private final EmployeeService service;
    public EmployeeController(EmployeeService service) {
        this.service = service;
    }
    @Operation(summary = "Find a employee by ID")
    @ApiResponse(responseCode = "200", description = "Record found")
    @ApiResponse(responseCode = "400", description = "Invalid ID")
    @ApiResponse(responseCode = "404", description = "Record not found")
    @GetMapping("/{id}")
    public EmployeeModel find(@PathVariable long id) {
        validateId(id);
        return service.find(id);
    }
    @PostMapping
    public ResponseEntity<EmployeeModel> create(@Valid @RequestBody EmployeeRequest request) {
        var model = service.create(request);
        return ResponseEntity.created(URI.create("/employees/" + model.id())).body(model);
    }
    @PutMapping("/{id}")
    public EmployeeModel update(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        validateId(id);
        return service.update(id, request);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { validateId(id); service.delete(id); }
    private void validateId(long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
    }
}
