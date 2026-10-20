package com.chetan.discovery.employeeservice;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
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
        var created = http.postForEntity("/employees", Map.of("name", "Created"), JsonNode.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getHeaders().getLocation());
        long id = created.getBody().path("id").asLong();
        assertBusinessBody(created.getBody(), id, "Created");
        String path = "/employees/" + id;
        assertTrue(repository.existsById(id));
        assertBusinessBody(http.getForObject(path, JsonNode.class), id, "Created");
        var updated = http.exchange(path, HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Updated")), JsonNode.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertBusinessBody(updated.getBody(), id, "Updated");
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
    /** Inspect raw JSON so deserialization cannot silently ignore leaked metadata. */
    private void assertBusinessBody(JsonNode body, long id, String name) {
        assertNotNull(body);
        assertTrue(body.isObject());
        assertEquals(2, body.size());
        assertEquals(id, body.path("id").asLong());
        assertEquals(name, body.path("name").asText());
    }
}
