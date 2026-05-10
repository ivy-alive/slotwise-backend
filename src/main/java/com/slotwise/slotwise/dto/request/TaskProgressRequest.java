package com.slotwise.slotwise.dto.request;

import lombok.Data;

@Data
public class TaskProgressRequest {
    private Integer consumedMinutes;
    private Boolean completed;
}
