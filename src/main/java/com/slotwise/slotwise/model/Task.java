package com.slotwise.slotwise.model;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    // Study task fields
    private Integer totalMinutes;
    private Integer remainingMinutes;
    private Boolean completed;

    @Enumerated(EnumType.STRING)
    private CycleType cycleType;

    private Integer cycleCount;

    @Enumerated(EnumType.STRING)
    private DayOfWeek preferredDay;

    private LocalTime preferredTime;

    private Integer cycleDebt = 0;

    private java.time.LocalDate dueDate;
    private java.time.LocalDate completedDate;
    private java.time.LocalDate lastDoneDate;

    // Workout task fields
    private Integer durationMinutes;

}