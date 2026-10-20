package com.chetan.discovery.employeeservice;

import jakarta.persistence.*;

/** Database representation; never serialized as an API response. */
@Entity
@Table(name = "employees")
public class EmployeeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_ids")
    @SequenceGenerator(name = "employee_ids", sequenceName = "employee_sequence", initialValue = 1000, allocationSize = 1)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    public EmployeeEntity() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
