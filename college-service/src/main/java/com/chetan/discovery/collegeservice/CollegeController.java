package com.chetan.discovery.collegeservice;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Demo endpoint returning instance identity so load balancing is visible. */
@RestController
@RequestMapping("/colleges")
public class CollegeController {
 private final Environment environment;
 public CollegeController(Environment environment) { this.environment = environment; }
 /** Returns illustrative data, not a persisted database record. */
 @GetMapping("/{id}")
 public Response find(@PathVariable long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
  return new Response(id, "College " + id, "college-service", environment.getProperty("local.server.port", environment.getProperty("server.port")), environment.getProperty("app.configuration.profile", "unknown"));
 }
 public record Response(long id, String name, String service, String instancePort, String configurationProfile) {}
}
