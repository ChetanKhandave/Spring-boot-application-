package com.chetan.discovery.employeeservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Verifies response contract and rejects invalid identifiers. */
@WebMvcTest(controllers = EmployeeController.class, properties = {"spring.cloud.config.enabled=false", "spring.config.location=classpath:/controller-test.properties"})
class EmployeeControllerTest {
 @Autowired MockMvc mvc;
 @org.springframework.test.context.bean.override.mockito.MockitoBean EmployeeService service;
 @org.junit.jupiter.api.BeforeEach void stubRecord() {
  org.mockito.Mockito.when(service.find(101L)).thenReturn(new EmployeeModel(101L, "Employee 101"));
 }
 @Test void returnsOnlyBusinessFields() throws Exception {
  mvc.perform(get("/employees/101")).andExpect(status().isOk())
     .andExpect(content().json("{\"id\":101,\"name\":\"Employee 101\"}", true));
 }
 @Test void createReturnsOnlyBusinessFieldsAndLocation() throws Exception {
  org.mockito.Mockito.when(service.create(new EmployeeRequest("Created"))).thenReturn(new EmployeeModel(1000L, "Created"));
  mvc.perform(post("/employees").contentType("application/json").content("{\"name\":\"Created\"}"))
     .andExpect(status().isCreated()).andExpect(header().string("Location", "/employees/1000"))
     .andExpect(content().json("{\"id\":1000,\"name\":\"Created\"}", true));
 }
 @Test void updateReturnsOnlyBusinessFields() throws Exception {
  org.mockito.Mockito.when(service.update(101L, new EmployeeRequest("Updated"))).thenReturn(new EmployeeModel(101L, "Updated"));
  mvc.perform(put("/employees/101").contentType("application/json").content("{\"name\":\"Updated\"}"))
     .andExpect(status().isOk()).andExpect(content().json("{\"id\":101,\"name\":\"Updated\"}", true));
 }
 @Test void deleteHasNoResponseBody() throws Exception {
  mvc.perform(delete("/employees/101")).andExpect(status().isNoContent()).andExpect(content().string(""));
  org.mockito.Mockito.verify(service).delete(101L);
 }
 @Test void rejectsNonPositiveId() throws Exception { mvc.perform(get("/employees/0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.errorId").isNotEmpty()); }
 @Test void rejectsNonNumericId() throws Exception { mvc.perform(get("/employees/abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.errorId").isNotEmpty()); }
 @Test void unknownIdReturns404() throws Exception {
  org.mockito.Mockito.when(service.find(999L)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
  mvc.perform(get("/employees/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_404"));
 }
}
