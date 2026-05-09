package com.slotwise.slotwise.dto.response;

import lombok.Data;

@Data
public class ConflictResponse {
    private Long allocationId;
    private String taskTitle;
    private Integer durationMinutes;
    private Integer availableMinutes;
    private String reason;
}