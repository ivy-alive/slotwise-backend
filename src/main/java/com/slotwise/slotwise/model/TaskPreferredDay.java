package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;

@Data
@Entity
@Table(name = "task_preferred_days")
public class TaskPreferredDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
}
