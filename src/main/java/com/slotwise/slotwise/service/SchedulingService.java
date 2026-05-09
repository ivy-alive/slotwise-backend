package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.response.ConflictResponse;
import com.slotwise.slotwise.dto.response.ScheduleResponse;
import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.*;
import com.slotwise.slotwise.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final DayEntryRepository dayEntryRepository;
    private final FreeSlotRepository freeSlotRepository;
    private final TaskRepository taskRepository;
    private final WorkoutScheduledDayRepository workoutScheduledDayRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final AllocationSlotRepository allocationSlotRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public ScheduleResponse schedule(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        DayEntry dayEntry = dayEntryRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));

        // Settle cycle debt from previous cycle before scheduling
        settleCycleDebt(date);

        // Delete existing allocations for the day and regenerate
        List<DailyTaskAllocation> existing = allocationRepository.findByDayEntryId(dayEntry.getId());
        allocationRepository.deleteAll(existing);

        // Load free slots ordered by start time
        List<FreeSlot> freeSlots = freeSlotRepository.findByDayEntryIdOrderByStart(dayEntry.getId());

        // Track the current pointer position in each FreeSlot
        Map<FreeSlot, LocalTime> slotPointers = new LinkedHashMap<>();
        for (FreeSlot slot : freeSlots) {
            slotPointers.put(slot, slot.getStart());
        }

        List<ScheduleResponse.AllocationResponse> allocationResponses = new ArrayList<>();
        List<ConflictResponse> conflictResponses = new ArrayList<>();

        // 1. Schedule workout tasks for today
        DayOfWeek todayDow = date.getDayOfWeek();
        List<WorkoutScheduledDay> todayWorkouts = workoutScheduledDayRepository.findByDayOfWeek(todayDow);

        for (WorkoutScheduledDay wsd : todayWorkouts) {
            Task task = wsd.getTask();
            int needed = task.getDurationMinutes();
            int available = calculateAvailableMinutes(slotPointers);

            DailyTaskAllocation allocation = new DailyTaskAllocation();
            allocation.setDayEntry(dayEntry);
            allocation.setTask(task);
            allocation.setPlannedMinutes(needed);
            allocation.setActualMinutes(0);
            allocation.setDone(false);

            if (available < needed) {
                // Not enough time — mark as conflict
                allocation.setConflict(true);
                allocationRepository.save(allocation);

                ConflictResponse conflict = new ConflictResponse();
                conflict.setAllocationId(allocation.getId());
                conflict.setTaskTitle(task.getTitle());
                conflict.setDurationMinutes(needed);
                conflict.setAvailableMinutes(available);
                conflict.setReason("Needs " + needed + " min but only " + available + " min available");
                conflictResponses.add(conflict);
            } else {
                // Enough time — schedule normally
                allocation.setConflict(false);
                allocationRepository.save(allocation);
                List<ScheduleResponse.SlotResponse> slotResponses = fillSlots(allocation, needed, slotPointers);

                ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
                ar.setAllocationId(allocation.getId());
                ar.setTaskTitle(task.getTitle());
                ar.setTaskType(task.getType());
                ar.setPlannedMinutes(needed);
                ar.setActualMinutes(0);
                ar.setDone(false);
                ar.setSlots(slotResponses);
                allocationResponses.add(ar);
            }
        }

        // 2. Schedule study tasks sorted by priority, with cycle debt boost
        List<Task> studyTasks = taskRepository.findByCompletedFalse()
                .stream()
                .filter(t -> t.getType() == TaskType.STUDY)
                .filter(t -> {
                    // Skip tasks whose dependencies are not yet completed
                    List<TaskDependency> deps = taskDependencyRepository.findByTaskId(t.getId());
                    return deps.stream()
                            .allMatch(d -> d.getDependsOn().getCompleted() != null && d.getDependsOn().getCompleted());
                })
                .sorted(Comparator.comparingInt((Task t) -> {
                    // Tasks behind on their cycle are boosted to highest priority
                    if (!isOnTrackForCycle(t, date))
                        return -1;
                    // Tasks with due date approaching within 3 days are boosted
                    if (t.getDueDate() != null) {
                        long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(date, t.getDueDate());
                        if (daysUntilDue <= 3 && daysUntilDue >= 0)
                            return 0;
                    }
                    return t.getPriority().ordinal();
                }))
                .collect(Collectors.toList());

        for (Task task : studyTasks) {
            int remaining = task.getRemainingMinutes() != null ? task.getRemainingMinutes() : 0;
            if (remaining <= 0)
                continue;

            int available = calculateAvailableMinutes(slotPointers);
            if (available <= 0)
                break;

            int planned = Math.min(remaining, available);

            DailyTaskAllocation allocation = new DailyTaskAllocation();
            allocation.setDayEntry(dayEntry);
            allocation.setTask(task);
            allocation.setPlannedMinutes(planned);
            allocation.setActualMinutes(0);
            allocation.setDone(false);
            allocation.setConflict(false);
            allocationRepository.save(allocation);

            List<ScheduleResponse.SlotResponse> slotResponses = fillSlots(allocation, planned, slotPointers);

            ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
            ar.setAllocationId(allocation.getId());
            ar.setTaskTitle(task.getTitle());
            ar.setTaskType(task.getType());
            ar.setPlannedMinutes(planned);
            ar.setActualMinutes(0);
            ar.setDone(false);
            ar.setSlots(slotResponses);
            allocationResponses.add(ar);
        }

        ScheduleResponse response = new ScheduleResponse();
        response.setDate(date);
        response.setAllocations(allocationResponses);
        response.setConflicts(conflictResponses);
        return response;
    }

    // Settle cycle debt from the previous cycle
    // Called at the start of each schedule generation
    private void settleCycleDebt(LocalDate date) {
        List<Task> studyTasks = taskRepository.findByCompletedFalse()
                .stream()
                .filter(t -> t.getType() == TaskType.STUDY)
                .filter(t -> t.getCycleType() != null && t.getCycleType() != CycleType.NONE)
                .collect(Collectors.toList());

        for (Task task : studyTasks) {
            LocalDate prevStart;
            LocalDate prevEnd;

            if (task.getCycleType() == CycleType.WEEKLY) {
                // Previous week range
                LocalDate thisWeekStart = date.with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                prevStart = thisWeekStart.minusWeeks(1);
                prevEnd = thisWeekStart.minusDays(1);
            } else {
                // Previous month range
                LocalDate thisMonthStart = date.withDayOfMonth(1);
                prevStart = thisMonthStart.minusMonths(1);
                prevEnd = thisMonthStart.minusDays(1);
            }

            // Count completed allocations in the previous cycle
            long completedLastCycle = allocationRepository.findByTaskId(task.getId())
                    .stream()
                    .filter(a -> {
                        LocalDate d = a.getDayEntry().getDate();
                        return !d.isBefore(prevStart) && !d.isAfter(prevEnd);
                    })
                    .filter(a -> a.getDone() != null && a.getDone())
                    .count();

            int required = task.getCycleCount() != null ? task.getCycleCount() : 0;
            int debt = (int) Math.max(required - completedLastCycle, 0);

            if (debt > 0) {
                // Only settle once per cycle — check if already scheduled this cycle
                LocalDate thisCycleStart = task.getCycleType() == CycleType.WEEKLY
                        ? date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        : date.withDayOfMonth(1);

                boolean alreadySettled = allocationRepository.findByTaskId(task.getId())
                        .stream()
                        .anyMatch(a -> {
                            LocalDate d = a.getDayEntry().getDate();
                            return !d.isBefore(thisCycleStart) && !d.isAfter(date);
                        });

                if (!alreadySettled) {
                    task.setCycleDebt(debt);
                    taskRepository.save(task);
                }
            }
        }
    }

    // Calculate total available minutes across all free slots
    private int calculateAvailableMinutes(Map<FreeSlot, LocalTime> slotPointers) {
        int total = 0;
        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            FreeSlot slot = entry.getKey();
            LocalTime pointer = entry.getValue();
            if (pointer.isBefore(slot.getEnd())) {
                total += (int) java.time.Duration.between(pointer, slot.getEnd()).toMinutes();
            }
        }
        return total;
    }

    // Fill a task into available free slots, splitting across slots if needed
    private List<ScheduleResponse.SlotResponse> fillSlots(
            DailyTaskAllocation allocation,
            int minutesNeeded,
            Map<FreeSlot, LocalTime> slotPointers) {

        List<ScheduleResponse.SlotResponse> slotResponses = new ArrayList<>();
        int remaining = minutesNeeded;

        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            if (remaining <= 0)
                break;

            FreeSlot slot = entry.getKey();
            LocalTime pointer = entry.getValue();
            if (!pointer.isBefore(slot.getEnd()))
                continue;

            int available = (int) java.time.Duration.between(pointer, slot.getEnd()).toMinutes();
            int use = Math.min(remaining, available);
            LocalTime end = pointer.plusMinutes(use);

            AllocationSlot as = new AllocationSlot();
            as.setDailyTaskAllocation(allocation);
            as.setFreeSlot(slot);
            as.setStart(pointer);
            as.setEnd(end);
            as.setMinutes(use);
            allocationSlotRepository.save(as);

            ScheduleResponse.SlotResponse sr = new ScheduleResponse.SlotResponse();
            sr.setStart(pointer);
            sr.setEnd(end);
            sr.setMinutes(use);
            slotResponses.add(sr);

            slotPointers.put(slot, end);
            remaining -= use;
        }

        return slotResponses;
    }

    // Check if a task is on track for its cycle requirement
    private boolean isOnTrackForCycle(Task task, LocalDate date) {
        if (task.getCycleType() == null || task.getCycleType() == CycleType.NONE) {
            return true;
        }

        LocalDate start;
        LocalDate end;

        if (task.getCycleType() == CycleType.WEEKLY) {
            start = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        } else {
            start = date.withDayOfMonth(1);
            end = date.withDayOfMonth(date.lengthOfMonth());
        }

        long completedCount = allocationRepository.findByTaskId(task.getId())
                .stream()
                .filter(a -> {
                    LocalDate allocationDate = a.getDayEntry().getDate();
                    return !allocationDate.isBefore(start) && !allocationDate.isAfter(end);
                })
                .filter(a -> a.getDone() != null && a.getDone())
                .count();

        int daysLeft = (int) java.time.temporal.ChronoUnit.DAYS.between(date, end) + 1;
        // Include cycle debt in the required count
        int cycleDebt = task.getCycleDebt() != null ? task.getCycleDebt() : 0;
        int remaining = (task.getCycleCount() + cycleDebt) - (int) completedCount;

        return remaining <= daysLeft;
    }

    public ScheduleResponse getSchedule(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        DayEntry dayEntry = dayEntryRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));

        List<DailyTaskAllocation> allocations = allocationRepository.findByDayEntryId(dayEntry.getId());

        List<ScheduleResponse.AllocationResponse> allocationResponses = allocations.stream()
                .filter(a -> !a.getConflict())
                .map(a -> {
                    ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
                    ar.setAllocationId(a.getId());
                    ar.setTaskTitle(a.getTask().getTitle());
                    ar.setTaskType(a.getTask().getType());
                    ar.setPlannedMinutes(a.getPlannedMinutes());
                    ar.setActualMinutes(a.getActualMinutes());
                    ar.setDone(a.getDone());
                    ar.setSlots(allocationSlotRepository.findByDailyTaskAllocationId(a.getId())
                            .stream()
                            .map(s -> {
                                ScheduleResponse.SlotResponse sr = new ScheduleResponse.SlotResponse();
                                sr.setStart(s.getStart());
                                sr.setEnd(s.getEnd());
                                sr.setMinutes(s.getMinutes());
                                return sr;
                            })
                            .collect(java.util.stream.Collectors.toList()));
                    return ar;
                })
                .collect(java.util.stream.Collectors.toList());

        List<ConflictResponse> conflictResponses = allocations.stream()
                .filter(a -> a.getConflict())
                .map(a -> {
                    ConflictResponse cr = new ConflictResponse();
                    cr.setAllocationId(a.getId());
                    cr.setTaskTitle(a.getTask().getTitle());
                    cr.setDurationMinutes(a.getPlannedMinutes());
                    cr.setReason("Insufficient time");
                    return cr;
                })
                .collect(java.util.stream.Collectors.toList());

        ScheduleResponse response = new ScheduleResponse();
        response.setDate(date);
        response.setAllocations(allocationResponses);
        response.setConflicts(conflictResponses);
        return response;
    }
}