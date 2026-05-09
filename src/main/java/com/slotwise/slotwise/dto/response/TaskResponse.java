package com.slotwise.slotwise.dto.response;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
    private LocalDate completedDate;
    private CycleType cycleType;
    private Integer cycleCount;
    private DayOfWeek preferredDay;
    private LocalTime preferredTime;
    private List<DoneSession> doneSessions;

    // Workout task
    private Integer durationMinutes;
    private List<DayOfWeek> scheduledDays;

    private List<TaskResponse> dependencies;

    @Data
    @AllArgsConstructor
    public static class DoneSession {
        private LocalDate date;
        private Integer actualMinutes;
    }
}
