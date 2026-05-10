package com.slotwise.slotwise.dto.request;

import lombok.Data;

@Data
public class AllocationUpdateRequest {
    private Boolean done;
    private Integer actualMinutes;
    private Integer newRemaining;
    private String memo;
}