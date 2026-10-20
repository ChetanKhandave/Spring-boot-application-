package com.chetan.discovery.employeeservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP -> controller -> service -> generated mapper -> JPA -> isolated H2 integration. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.location=classpath:/controller-test.properties",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:employee-integration;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "app.demo-data.enabled=false"
})
class EmployeePersistenceIT {
    @Autowired TestRestTemplate http;
    @Autowired EmployeeRepository repository;
    @Test void committedCrudIsVisibleAcrossSeparateHttpRequests() {
        var created = http.postForEntity("/employees", Map.of("name", "Created"), EmployeeModel.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getHeaders().getLocation());
        long id = created.getBody().id();
        String path = "/employees/" + id;
        assertTrue(repository.existsById(id));
        assertEquals("Created", http.getForObject(path, EmployeeModel.class).name());
        var updated = http.exchange(path, HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Updated")), EmployeeModel.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals("Updated", repository.findById(id).orElseThrow().getName());
        assertEquals(HttpStatus.NO_CONTENT, http.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class).getStatusCode());
        assertFalse(repository.existsById(id));
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(path, String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, http.exchange(path, HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Missing")), String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, http.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, String.class).getStatusCode());
    }
    @Test void invalidRequestsDoNotWriteToDatabase() {
        long before = repository.count();
        for (String name : new String[] {"", "   ", "x".repeat(101)}) {
            assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/employees", Map.of("name", name), String.class).getStatusCode());
        }
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/employees", Map.of(), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.getForEntity("/employees/0", String.class).getStatusCode());
        assertEquals(before, repository.count());
    }
}
