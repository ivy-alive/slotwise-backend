package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne
    @JoinColumn(name = "depends_on_id")
    private Task dependsOn;
}