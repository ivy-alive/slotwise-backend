package com.slotwise.slotwise.dto.request;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Data
public class TaskRequest {
    private String title;
    private TaskType type;
    private Priority priority;
    private Integer totalMinutes;
    private Boolean splittable;

    // ONE_TIME only
    private LocalDate ddl;

    // RECURRING only
    private CycleType cycleType;
    private Integer cycleCount;
    private List<DayOfWeek> preferredDays;

    private List<Long> dependsOnIds;
}
