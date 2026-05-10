package com.slotwise.slotwise.controller;

import com.slotwise.slotwise.dto.request.AllocationUpdateRequest;
import com.slotwise.slotwise.dto.request.DayEntryRequest;
import com.slotwise.slotwise.dto.response.DayEntryResponse;
import com.slotwise.slotwise.dto.response.ScheduleResponse;
import com.slotwise.slotwise.model.FreeSlot;
import com.slotwise.slotwise.service.DayEntryService;
import com.slotwise.slotwise.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/day-entries")
@RequiredArgsConstructor
public class DayEntryController {

    private final DayEntryService dayEntryService;
    private final SchedulingService schedulingService;

    @PostMapping
    public DayEntryResponse createDayEntry(@RequestBody DayEntryRequest request) {
        return dayEntryService.createDayEntry(request);
    }

    @GetMapping("/{date}")
    public DayEntryResponse getDayEntry(@PathVariable String date) {
        return dayEntryService.getDayEntry(date);
    }

    @PostMapping("/{date}/schedule")
    public ScheduleResponse schedule(@PathVariable String date) {
        return schedulingService.schedule(date);
    }

    @PutMapping("/{date}/allocations/{allocationId}")
    public void updateAllocation(
            @PathVariable String date,
            @PathVariable Long allocationId,
            @RequestBody AllocationUpdateRequest request) {
        dayEntryService.updateAllocation(allocationId, request);
    }

    @PostMapping("/{date}/free-slots")
    public FreeSlot addFreeSlot(@PathVariable String date,
            @RequestBody DayEntryRequest.FreeSlotRequest request) {
        return dayEntryService.addFreeSlot(date, request);
    }

    @PutMapping("/{date}/free-slots/{slotId}")
    public FreeSlot updateFreeSlot(@PathVariable String date,
            @PathVariable Long slotId,
            @RequestBody DayEntryRequest.FreeSlotRequest request) {
        return dayEntryService.updateFreeSlot(slotId, request);
    }

    @DeleteMapping("/{date}/free-slots/{slotId}")
    public void deleteFreeSlot(@PathVariable String date,
            @PathVariable Long slotId) {
        dayEntryService.deleteFreeSlot(slotId);
    }

    @GetMapping("/{date}/schedule")
    public ScheduleResponse getSchedule(@PathVariable String date) {
        return schedulingService.getSchedule(date);
    }
}