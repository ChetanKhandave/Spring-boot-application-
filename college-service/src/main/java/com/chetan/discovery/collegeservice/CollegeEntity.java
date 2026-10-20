package com.chetan.discovery.collegeservice;

import jakarta.persistence.*;

/** Database representation; never serialized as an API response. */
@Entity
@Table(name = "colleges")
public class CollegeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "college_ids")
    @SequenceGenerator(name = "college_ids", sequenceName = "college_sequence", initialValue = 1000, allocationSize = 1)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    public CollegeEntity() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
