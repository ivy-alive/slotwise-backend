package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;

@Data
@Entity
@Table(name = "workout_scheduled_days")
public class WorkoutScheduledDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
}