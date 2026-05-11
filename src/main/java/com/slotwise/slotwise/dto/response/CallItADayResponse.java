package com.slotwise.slotwise.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CallItADayResponse {
    private LocalDate nextDate;
    private int carryOverCount;
    private ScheduleResponse schedule;
}
