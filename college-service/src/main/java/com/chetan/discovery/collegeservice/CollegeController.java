package com.chetan.discovery.collegeservice;

import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;

/** HTTP adapter. Entities remain behind the service boundary. */
@RestController
@RequestMapping("/colleges")
public class CollegeController {
    private final CollegeService service;
    private final Environment environment;
    public CollegeController(CollegeService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }
    @Operation(summary = "Find a college by ID")
    @ApiResponse(responseCode = "200", description = "Record found")
    @ApiResponse(responseCode = "400", description = "Invalid ID")
    @ApiResponse(responseCode = "404", description = "Record not found")
    @GetMapping("/{id}")
    public Response find(@PathVariable long id) {
        validateId(id);
        return response(service.find(id));
    }
    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CollegeRequest request) {
        var model = service.create(request);
        return ResponseEntity.created(URI.create("/colleges/" + model.id())).body(response(model));
    }
    @PutMapping("/{id}")
    public Response update(@PathVariable long id, @Valid @RequestBody CollegeRequest request) {
        validateId(id);
        return response(service.update(id, request));
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { validateId(id); service.delete(id); }
    private void validateId(long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
    }
    private Response response(CollegeModel model) {
        return new Response(model.id(), model.name(), "college-service",
            environment.getProperty("local.server.port", environment.getProperty("server.port")),
            environment.getProperty("app.configuration.profile", "unknown"));
    }
    /** API model with instance metadata retained for the discovery/load-balancing demonstration. */
    public record Response(Long id, String name, String service, String instancePort,
                           String configurationProfile) {}
}
