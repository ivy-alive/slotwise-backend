package com.slotwise.slotwise.dto.response;

import com.slotwise.slotwise.enums.TaskType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class ScheduleResponse {
    private LocalDate date;
    private boolean closed;
    private List<AllocationResponse> allocations;
    private List<ConflictResponse> conflicts;

    @Data
    public static class AllocationResponse {
        private Long allocationId;
        private String taskTitle;
        private TaskType taskType;
        private Integer plannedMinutes;
        private Integer actualMinutes;
        private Boolean done;
        private boolean carriedOver;
        private String memo;
        private Integer consumedMinutes;
        private Integer totalMinutes;
        private LocalDate ddl;
        private List<SlotResponse> slots;
    }

    @Data
    public static class SlotResponse {
        private LocalTime start;
        private LocalTime end;
        private Integer minutes;
    }
}