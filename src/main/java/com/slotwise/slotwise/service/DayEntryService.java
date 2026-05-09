package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.AllocationUpdateRequest;
import com.slotwise.slotwise.dto.request.DayEntryRequest;
import com.slotwise.slotwise.dto.response.DayEntryResponse;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.AllocationSlot;
import com.slotwise.slotwise.model.DailyTaskAllocation;
import com.slotwise.slotwise.model.DayEntry;
import com.slotwise.slotwise.model.FreeSlot;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.repository.AllocationSlotRepository;
import com.slotwise.slotwise.repository.DailyTaskAllocationRepository;
import com.slotwise.slotwise.repository.DayEntryRepository;
import com.slotwise.slotwise.repository.FreeSlotRepository;
import com.slotwise.slotwise.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DayEntryService {

    private final DayEntryRepository dayEntryRepository;
    private final FreeSlotRepository freeSlotRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final TaskRepository taskRepository;
    private final AllocationSlotRepository allocationSlotRepository;

    public DayEntryResponse createDayEntry(DayEntryRequest request) {
        DayEntry dayEntry = new DayEntry();
        dayEntry.setDate(request.getDate());
        dayEntryRepository.save(dayEntry);

        if (request.getFreeSlots() != null) {
            for (var slotRequest : request.getFreeSlots()) {
                FreeSlot freeSlot = new FreeSlot();
                freeSlot.setDayEntry(dayEntry);
                freeSlot.setStart(slotRequest.getStart());
                freeSlot.setEnd(slotRequest.getEnd());
                freeSlotRepository.save(freeSlot);
            }
        }

        return toResponse(dayEntry);
    }

    public DayEntryResponse getDayEntry(String date) {
        DayEntry dayEntry = dayEntryRepository.findByDate(java.time.LocalDate.parse(date))
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + date));
        return toResponse(dayEntry);
    }

    public FreeSlot updateFreeSlot(Long slotId, DayEntryRequest.FreeSlotRequest request) {
        FreeSlot slot = freeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("FreeSlot not found: " + slotId));
        slot.setStart(request.getStart());
        slot.setEnd(request.getEnd());
        return freeSlotRepository.save(slot);
    }

    public void deleteFreeSlot(Long slotId) {
        FreeSlot slot = freeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("FreeSlot not found: " + slotId));

        List<AllocationSlot> allocationSlots = allocationSlotRepository.findByFreeSlotId(slotId);
        allocationSlotRepository.deleteAll(allocationSlots);

        freeSlotRepository.delete(slot);
    }

    public FreeSlot addFreeSlot(String date, DayEntryRequest.FreeSlotRequest request) {
        DayEntry dayEntry = dayEntryRepository.findByDate(java.time.LocalDate.parse(date))
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + date));
        FreeSlot slot = new FreeSlot();
        slot.setDayEntry(dayEntry);
        slot.setStart(request.getStart());
        slot.setEnd(request.getEnd());
        return freeSlotRepository.save(slot);
    }

    public UpdateAllocationResult updateAllocation(Long allocationId, AllocationUpdateRequest request) {
        DailyTaskAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found: " + allocationId));

        Task task = allocation.getTask();
        boolean shouldReschedule = false;
        boolean askReschedule = false;
        int minutesFreed = 0;

        allocation.setDone(request.getDone());
        allocation.setActualMinutes(request.getActualMinutes());
        allocationRepository.save(allocation);

        if (request.getDone()) {
            // Done
            if (task.getType() == TaskType.ONE_TIME) {
                task.setRemainingMinutes(
                        Math.max(task.getRemainingMinutes() - request.getActualMinutes(), 0));
                task.setLastDoneDate(allocation.getDayEntry().getDate());
                if (task.getRemainingMinutes() == 0) {
                    task.setCompleted(true);
                    task.setCompletedDate(allocation.getDayEntry().getDate());
                }
                taskRepository.save(task);
            }

            int diff = allocation.getPlannedMinutes() - request.getActualMinutes();
            if (diff > 0) {
                // Finished faster than planned — ask user if they want to reschedule
                askReschedule = true;
                minutesFreed = diff;
            } else if (diff < 0) {
                // Took longer than planned — auto reschedule
                shouldReschedule = true;
            }
        } else {
            // Not done — update remaining minutes
            int newRemaining = request.getNewRemaining() != null
                    ? request.getNewRemaining()
                    : Math.max(task.getRemainingMinutes() - request.getActualMinutes(), 0);
            task.setRemainingMinutes(newRemaining);
            taskRepository.save(task);
            shouldReschedule = true;
        }

        return new UpdateAllocationResult(shouldReschedule, askReschedule, minutesFreed);
    }

    private DayEntryResponse toResponse(DayEntry dayEntry) {
        DayEntryResponse response = new DayEntryResponse();
        response.setId(dayEntry.getId());
        response.setDate(dayEntry.getDate());

        List<DayEntryResponse.FreeSlotResponse> slotResponses = freeSlotRepository
                .findByDayEntryIdOrderByStart(dayEntry.getId())
                .stream()
                .map(slot -> {
                    DayEntryResponse.FreeSlotResponse sr = new DayEntryResponse.FreeSlotResponse();
                    sr.setId(slot.getId());
                    sr.setStart(slot.getStart());
                    sr.setEnd(slot.getEnd());
                    return sr;
                })
                .collect(Collectors.toList());

        response.setFreeSlots(slotResponses);
        return response;
    }

    public record UpdateAllocationResult(
            boolean shouldReschedule,
            boolean askReschedule,
            int minutesFreed) {
    }
}
