package com.slotwise.slotwise.dto.response;

import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Data
public class TaskResponse {
    private Long id;
    private String title;
    private TaskType type;
    private Priority priority;
    private Integer totalMinutes;
    private Integer remainingMinutes;
    private Boolean splittable;
    private Boolean completed;
    private LocalDate completedDate;

    // ONE_TIME only
    private LocalDate ddl;

    // RECURRING only
    private CycleType cycleType;
    private Integer cycleCount;
    private List<DayOfWeek> preferredDays;

    private List<TaskResponse> dependencies;
    private List<DoneSession> doneSessions;

    @Data
    @AllArgsConstructor
    public static class DoneSession {
        private LocalDate date;
        private Integer actualMinutes;
    }
}
