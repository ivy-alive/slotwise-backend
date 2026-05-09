package com.slotwise.slotwise.dto.response;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Data
public class WorkoutStatsResponse {
    private String period;
    private List<WorkoutTaskStats> workoutStats;

    @Data
    public static class WorkoutTaskStats {
        private Long taskId;
        private String title;
        private List<DayOfWeek> scheduledDays;
        private Integer plannedCount;
        private Integer actualCount;
        private List<AllocationSummary> allocations;
    }

    @Data
    public static class AllocationSummary {
        private LocalDate date;
        private Boolean done;
    }
}