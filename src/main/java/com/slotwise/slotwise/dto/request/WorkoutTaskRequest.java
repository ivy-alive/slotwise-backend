package com.slotwise.slotwise.dto.request;

import com.slotwise.slotwise.enums.Priority;
import lombok.Data;

import java.time.DayOfWeek;
import java.util.List;

@Data
public class WorkoutTaskRequest {
    private String title;
    private Priority priority;
    private Integer durationMinutes;
    private List<DayOfWeek> scheduledDays;
}