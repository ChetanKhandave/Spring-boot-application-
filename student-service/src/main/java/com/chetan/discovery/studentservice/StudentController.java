package com.chetan.discovery.studentservice;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Demo endpoint returning instance identity so load balancing is visible. */
@RestController
@RequestMapping("/students")
public class StudentController {
 private final Environment environment;
 public StudentController(Environment environment) { this.environment = environment; }
 /** Returns illustrative data, not a persisted database record. */
 @GetMapping("/{id}")
 public Response find(@PathVariable long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
  return new Response(id, "Student " + id, "student-service", environment.getProperty("local.server.port", environment.getProperty("server.port")));
 }
 public record Response(long id, String name, String service, String instancePort) {}
}
