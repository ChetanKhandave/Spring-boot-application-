package com.chetan.discovery.employeeservice;

import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Optional fixture for the local demo; ordinary writes use the JPA repository and MapStruct. */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class EmployeeDemoData implements ApplicationRunner {
    private final EntityManager entityManager;
    public EmployeeDemoData(EntityManager entityManager) { this.entityManager = entityManager; }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Native JPA query deliberately reserves fixture ID 101; generated IDs start at 1000.
        // INSERT ... SELECT keeps an existing fixture unchanged on subsequent startups.
        entityManager.createNativeQuery("INSERT INTO employees (id, name) "
                + "SELECT 101, :name WHERE NOT EXISTS (SELECT 1 FROM employees WHERE id = 101)")
            .setParameter("name", "Employee 101").executeUpdate();
    }
}
