package com.slotwise.slotwise.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class DayEntryRequest {
    private LocalDate date;
    private List<FreeSlotRequest> freeSlots;

    @Data
    public static class FreeSlotRequest {
        private LocalTime start;
        private LocalTime end;
    }
}