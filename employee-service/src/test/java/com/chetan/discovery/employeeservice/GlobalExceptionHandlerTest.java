package com.chetan.discovery.employeeservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/** Exercises failure scenarios without adding failure endpoints to the production application. */
class GlobalExceptionHandlerTest {
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void unexpectedFailureIsSanitized() throws Exception {
        mvc.perform(get("/test/fail")).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.code").value("HTTP_500"))
                .andExpect(jsonPath("$.path").value("/test/fail"))
                .andExpect(jsonPath("$.errorId", matchesPattern("[a-f0-9-]{36}")))
                .andExpect(content().string(not(containsString("secret-database-password"))));
    }
    @Test void explicitStatusIsPreservedWithoutLeakingReason() throws Exception {
        mvc.perform(get("/test/status")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(content().string(not(containsString("internal-record-details"))));
    }
    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/test/body").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void methodNotAllowedRetainsAllowHeader() throws Exception {
        mvc.perform(post("/test/fail")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }
    @RestController
    static class FailureController {
        @GetMapping("/test/fail") String fail() { throw new IllegalStateException("secret-database-password"); }
        @GetMapping("/test/status") String missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "internal-record-details"); }
        @PostMapping("/test/body") Map<String, Object> body(@RequestBody Map<String, Object> body) { return body; }
    }
}
