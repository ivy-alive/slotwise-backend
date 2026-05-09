package com.slotwise.slotwise.dto.response;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Data
public class TaskResponse {
    private Long id;
    private String title;
    private TaskType type;
    private Priority priority;

    // Study task
    private Integer totalMinutes;
    private Integer remainingMinutes;
    private Boolean completed;
    private CycleType cycleType;
    private Integer cycleCount;
    private DayOfWeek preferredDay;
    private LocalTime preferredTime;

    // Workout task
    private Integer durationMinutes;
    private List<DayOfWeek> scheduledDays;

    private List<TaskResponse> dependencies;
}