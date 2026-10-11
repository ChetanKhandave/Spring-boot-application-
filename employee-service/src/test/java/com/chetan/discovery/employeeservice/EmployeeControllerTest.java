package com.chetan.discovery.employeeservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Verifies response contract and rejects invalid identifiers. */
@WebMvcTest(controllers = EmployeeController.class, properties = {"spring.cloud.config.enabled=false", "spring.config.import="})
class EmployeeControllerTest {
 @Autowired MockMvc mvc;
 @Test void returnsServiceIdentity() throws Exception { mvc.perform(get("/employees/101")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(101)).andExpect(jsonPath("$.service").value("employee-service")); }
 @Test void rejectsNonPositiveId() throws Exception { mvc.perform(get("/employees/0")).andExpect(status().isBadRequest()); }
 @Test void rejectsNonNumericId() throws Exception { mvc.perform(get("/employees/abc")).andExpect(status().isBadRequest()); }
}
