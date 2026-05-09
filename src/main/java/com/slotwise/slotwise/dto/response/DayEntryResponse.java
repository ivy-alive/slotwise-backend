package com.slotwise.slotwise.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class DayEntryResponse {
    private Long id;
    private LocalDate date;
    private List<FreeSlotResponse> freeSlots;

    @Data
    public static class FreeSlotResponse {
        private Long id;
        private LocalTime start;
        private LocalTime end;
    }
}