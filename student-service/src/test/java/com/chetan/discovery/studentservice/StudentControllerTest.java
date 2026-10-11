package com.chetan.discovery.studentservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Verifies response contract and rejects invalid identifiers. */
@WebMvcTest(controllers = StudentController.class, properties = "spring.cloud.config.enabled=false")
class StudentControllerTest {
 @Autowired MockMvc mvc;
 @Test void returnsServiceIdentity() throws Exception { mvc.perform(get("/students/101")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(101)).andExpect(jsonPath("$.service").value("student-service")); }
 @Test void rejectsNonPositiveId() throws Exception { mvc.perform(get("/students/0")).andExpect(status().isBadRequest()); }
 @Test void rejectsNonNumericId() throws Exception { mvc.perform(get("/students/abc")).andExpect(status().isBadRequest()); }
}
