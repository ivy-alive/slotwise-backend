package com.slotwise.slotwise.dto.response;

import com.slotwise.slotwise.enums.CycleType;
import lombok.Data;

@Data
public class CycleProgressResponse {
    private Long taskId;
    private String title;
    private CycleType cycleType;
    private Integer cycleCount;
    private Integer completedThisCycle;
    private Integer remainingThisCycle;
    private Integer daysLeftInCycle;
    private Boolean onTrack;
}