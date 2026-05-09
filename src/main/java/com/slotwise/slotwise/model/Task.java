package com.slotwise.slotwise.model;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

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

    private Integer totalMinutes;
    private Integer remainingMinutes;
    private Boolean splittable;
    private Boolean completed;
    private LocalDate completedDate;
    private LocalDate lastDoneDate;

    // ONE_TIME only
    private LocalDate ddl;

    // RECURRING only
    @Enumerated(EnumType.STRING)
    private CycleType cycleType;

    private Integer cycleCount;
    private Integer cycleDebt = 0;
}
