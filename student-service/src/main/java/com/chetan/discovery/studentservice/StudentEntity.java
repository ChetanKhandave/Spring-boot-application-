package com.chetan.discovery.studentservice;

import jakarta.persistence.*;

/** Database representation; never serialized as an API response. */
@Entity
@Table(name = "students")
public class StudentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "student_ids")
    @SequenceGenerator(name = "student_ids", sequenceName = "student_sequence", initialValue = 1000, allocationSize = 1)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    public StudentEntity() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
