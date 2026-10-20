package com.chetan.discovery.collegeservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Verifies response contract and rejects invalid identifiers. */
@WebMvcTest(controllers = CollegeController.class, properties = {"spring.cloud.config.enabled=false", "spring.config.location=classpath:/controller-test.properties"})
class CollegeControllerTest {
 @Autowired MockMvc mvc;
 @org.springframework.test.context.bean.override.mockito.MockitoBean CollegeService service;
 @org.junit.jupiter.api.BeforeEach void stubRecord() {
  org.mockito.Mockito.when(service.find(101L)).thenReturn(new CollegeModel(101L, "College 101"));
 }
 @Test void returnsServiceIdentity() throws Exception { mvc.perform(get("/colleges/101")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(101)).andExpect(jsonPath("$.service").value("college-service")); }
 @Test void rejectsNonPositiveId() throws Exception { mvc.perform(get("/colleges/0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.errorId").isNotEmpty()); }
 @Test void rejectsNonNumericId() throws Exception { mvc.perform(get("/colleges/abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.errorId").isNotEmpty()); }
 @Test void unknownIdReturns404() throws Exception {
  org.mockito.Mockito.when(service.find(999L)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
  mvc.perform(get("/colleges/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_404"));
 }
}
