package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.AllocationUpdateRequest;
import com.slotwise.slotwise.dto.request.DayEntryRequest;
import com.slotwise.slotwise.dto.response.CallItADayResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DayEntryService {

    private final DayEntryRepository dayEntryRepository;
    private final FreeSlotRepository freeSlotRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final TaskRepository taskRepository;
    private final AllocationSlotRepository allocationSlotRepository;
    private final SchedulingService schedulingService;

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

    public void updateAllocation(Long allocationId, AllocationUpdateRequest request) {
        DailyTaskAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found: " + allocationId));

        Task task = allocation.getTask();
        Boolean prevDone = allocation.getDone();
        int prevActualMinutes = allocation.getActualMinutes() != null ? allocation.getActualMinutes() : 0;

        // Reverse the previous contribution to consumedMinutes before applying new values
        if (prevDone != null) {
            boolean prevContributed = (task.getType() == TaskType.ONE_TIME && Boolean.TRUE.equals(prevDone))
                    || Boolean.FALSE.equals(prevDone);
            if (prevContributed) {
                int reversed = Math.max(task.getConsumedMinutes() - prevActualMinutes, 0);
                task.setConsumedMinutes(reversed);
                if (task.getType() == TaskType.ONE_TIME && Boolean.TRUE.equals(prevDone)) {
                    task.setCompleted(false);
                    task.setCompletedDate(null);
                } else if (Boolean.TRUE.equals(task.getCompleted()) && reversed < task.getTotalMinutes()) {
                    task.setCompleted(false);
                    task.setCompletedDate(null);
                }
            }
        }

        allocation.setDone(request.getDone());
        allocation.setActualMinutes(request.getActualMinutes());
        allocation.setMemo(request.getMemo());
        allocationRepository.save(allocation);

        if (request.getDone()) {
            if (task.getType() == TaskType.ONE_TIME) {
                int newConsumed = Math.min(
                        task.getConsumedMinutes() + request.getActualMinutes(),
                        task.getTotalMinutes());
                task.setConsumedMinutes(newConsumed);
                task.setLastDoneDate(allocation.getDayEntry().getDate());
                task.setCompleted(true);
                task.setCompletedDate(allocation.getDayEntry().getDate());
            }
        } else {
            int newConsumed = request.getNewRemaining() != null
                    ? Math.max(task.getTotalMinutes() - request.getNewRemaining(), 0)
                    : Math.min(task.getConsumedMinutes() + request.getActualMinutes(), task.getTotalMinutes());
            task.setConsumedMinutes(newConsumed);
        }
        taskRepository.save(task);
    }

    public void deleteAllocationLog(Long allocationId) {
        DailyTaskAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found: " + allocationId));

        Boolean prevDone = allocation.getDone();
        if (prevDone == null) return;

        Task task = allocation.getTask();
        int prevActualMinutes = allocation.getActualMinutes() != null ? allocation.getActualMinutes() : 0;

        boolean prevContributed = (task.getType() == TaskType.ONE_TIME && Boolean.TRUE.equals(prevDone))
                || Boolean.FALSE.equals(prevDone);
        if (prevContributed) {
            int reversed = Math.max(task.getConsumedMinutes() - prevActualMinutes, 0);
            task.setConsumedMinutes(reversed);
            if (task.getType() == TaskType.ONE_TIME && Boolean.TRUE.equals(prevDone)) {
                task.setCompleted(false);
                task.setCompletedDate(null);
            } else if (Boolean.TRUE.equals(task.getCompleted()) && reversed < task.getTotalMinutes()) {
                task.setCompleted(false);
                task.setCompletedDate(null);
            }
            taskRepository.save(task);
        }

        allocation.setDone(null);
        allocation.setActualMinutes(0);
        allocation.setMemo(null);
        allocationRepository.save(allocation);
    }

    public CallItADayResponse callItADay(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        DayEntry dayEntry = dayEntryRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));

        List<DailyTaskAllocation> all = allocationRepository.findByDayEntryId(dayEntry.getId());

        // Mark unlogged allocations as carried over instead of deleting them
        List<DailyTaskAllocation> unlogged = all.stream()
                .filter(a -> a.getDone() == null)
                .collect(Collectors.toList());
        unlogged.forEach(a -> a.setCarriedOver(true));
        allocationRepository.saveAll(unlogged);

        // Carry over tasks that are not fully done (unlogged + explicitly not-done)
        Set<Long> carryOverTaskIds = all.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDone()))
                .map(a -> a.getTask().getId())
                .collect(Collectors.toSet());

        dayEntry.setClosed(true);
        dayEntryRepository.save(dayEntry);

        LocalDate nextDate = date.plusDays(1);

        CallItADayResponse response = new CallItADayResponse();
        response.setNextDate(nextDate);
        response.setCarryOverCount(carryOverTaskIds.size());

        dayEntryRepository.findByDate(nextDate).ifPresent(nextEntry ->
                response.setSchedule(schedulingService.schedule(nextDate.toString(), carryOverTaskIds)));

        return response;
    }

    public void reopenDay(String dateStr) {
        DayEntry dayEntry = dayEntryRepository.findByDate(LocalDate.parse(dateStr))
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));
        dayEntry.setClosed(false);
        dayEntryRepository.save(dayEntry);
    }

    private DayEntryResponse toResponse(DayEntry dayEntry) {
        DayEntryResponse response = new DayEntryResponse();
        response.setId(dayEntry.getId());
        response.setDate(dayEntry.getDate());
        response.setClosed(dayEntry.isClosed());

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

}
