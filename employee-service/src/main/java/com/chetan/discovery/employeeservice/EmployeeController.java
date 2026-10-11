package com.chetan.discovery.employeeservice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Demo endpoint returning instance identity so load balancing is visible. */
@Tag(name = "Employees", description = "Demo employee lookup endpoints")
@RestController
@RequestMapping("/employees")
public class EmployeeController {
 private static final Logger logger = LoggerFactory.getLogger(EmployeeController.class);
 private final Environment environment;
 public EmployeeController(Environment environment) { this.environment = environment; }
 /** Returns illustrative data, not a persisted database record. */
 @Operation(summary = "Find a employee by ID", description = "Returns demo data for any positive ID. Instance port shows which service handled the request.",
   responses = {
     @ApiResponse(responseCode = "200", description = "Demo record returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
     @ApiResponse(responseCode = "400", description = "ID is nonnumeric or not positive", content = @Content)
   })
 @GetMapping("/{id}")
 public Response find(@Parameter(description = "Positive identifier", example = "101", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
  // Trace and span IDs are added by Micrometer; no request payload or personal data is logged.
  logger.info("Demo request processed by instance {}", environment.getProperty("server.port"));
  return new Response(id, "Employee " + id, "employee-service", environment.getProperty("local.server.port", environment.getProperty("server.port")), environment.getProperty("app.configuration.profile", "unknown"));
 }
 @Schema(description = "Demo record and responding instance metadata")
 public record Response(
   @Schema(description = "Requested identifier", example = "101") long id,
   @Schema(description = "Illustrative display name", example = "Employee 101") String name,
   @Schema(description = "Logical service name", example = "employee-service") String service,
   @Schema(description = "Responding instance port", example = "8081") String instancePort,
   @Schema(description = "Profile marker loaded from Config Server", example = "dev") String configurationProfile) {}
}
