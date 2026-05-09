package com.slotwise.slotwise.dto.request;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Data
public class StudyTaskRequest {
    private String title;
    private Priority priority;
    private Integer totalMinutes;
    private CycleType cycleType;
    private Integer cycleCount;
    private DayOfWeek preferredDay;
    private LocalTime preferredTime;
    private java.time.LocalDate dueDate;
    private List<Long> dependsOnIds;
}