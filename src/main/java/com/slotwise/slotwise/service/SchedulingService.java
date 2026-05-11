package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.response.ConflictResponse;
import com.slotwise.slotwise.dto.response.ScheduleResponse;
import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
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
    private final TaskPreferredDayRepository preferredDayRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final AllocationSlotRepository allocationSlotRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public ScheduleResponse schedule(String dateStr) {
        return schedule(dateStr, java.util.Collections.emptySet());
    }

    public ScheduleResponse schedule(String dateStr, java.util.Set<Long> forceTaskIds) {
        LocalDate date = LocalDate.parse(dateStr);
        DayEntry dayEntry = dayEntryRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));

        // Closed days are immutable — return existing schedule without re-running the algorithm
        if (dayEntry.isClosed()) {
            return getSchedule(dateStr);
        }

        settleCycleDebt(date);

        List<DailyTaskAllocation> existing = allocationRepository.findByDayEntryId(dayEntry.getId());
        List<DailyTaskAllocation> loggedAllocations = existing.stream()
                .filter(a -> a.getDone() != null)
                .collect(Collectors.toList());
        List<DailyTaskAllocation> carriedOverAllocations = existing.stream()
                .filter(a -> a.getDone() == null && a.isCarriedOver())
                .collect(Collectors.toList());
        // Only delete truly fresh unscheduled allocations — not carried-over ones
        List<DailyTaskAllocation> unloggedAllocations = existing.stream()
                .filter(a -> a.getDone() == null && !a.isCarriedOver())
                .collect(Collectors.toList());
        allocationRepository.deleteAll(unloggedAllocations);

        // Both logged and carried-over tasks are already accounted for — don't re-schedule them
        Set<Long> protectedTaskIds = java.util.stream.Stream
                .concat(loggedAllocations.stream(), carriedOverAllocations.stream())
                .map(a -> a.getTask().getId())
                .collect(Collectors.toSet());

        List<FreeSlot> freeSlots = freeSlotRepository.findByDayEntryIdOrderByStart(dayEntry.getId());
        Map<FreeSlot, LocalTime> slotPointers = new LinkedHashMap<>();
        for (FreeSlot slot : freeSlots) {
            slotPointers.put(slot, slot.getStart());
        }

        // Collect all allocationSlots from logged and carried-over allocations, grouped by FreeSlot id.
        // Used both to initialise slot pointers (contiguous-only advance) and to block new tasks from
        // writing into time already occupied by preserved allocations.
        Map<Long, List<AllocationSlot>> reservedBySlot = new LinkedHashMap<>();
        java.util.stream.Stream.concat(loggedAllocations.stream(), carriedOverAllocations.stream())
                .forEach(alloc -> allocationSlotRepository.findByDailyTaskAllocationId(alloc.getId())
                        .forEach(as -> reservedBySlot
                                .computeIfAbsent(as.getFreeSlot().getId(), k -> new ArrayList<>())
                                .add(as)));

        for (FreeSlot slot : freeSlots) {
            List<AllocationSlot> used = reservedBySlot.getOrDefault(slot.getId(), Collections.emptyList());
            used.sort(Comparator.comparing(AllocationSlot::getStart));
            LocalTime pointer = slot.getStart();
            for (AllocationSlot as : used) {
                if (!as.getStart().isAfter(pointer)) {
                    if (as.getEnd().isAfter(pointer)) {
                        pointer = as.getEnd();
                    }
                } else {
                    break; // gap before this block — earlier time is still free
                }
            }
            slotPointers.put(slot, pointer);
        }

        List<ScheduleResponse.AllocationResponse> allocationResponses = new ArrayList<>();
        List<ConflictResponse> conflictResponses = new ArrayList<>();

        // Step 1: RECURRING tasks whose preferred days include today
        DayOfWeek todayDow = date.getDayOfWeek();
        Set<Long> handledRecurringIds = new HashSet<>();

        List<TaskPreferredDay> todayPreferred = preferredDayRepository.findByDayOfWeek(todayDow);
        List<Task> preferredTodayTasks = todayPreferred.stream()
                .map(TaskPreferredDay::getTask)
                .filter(t -> t.getType() == TaskType.RECURRING)
                .filter(t -> !protectedTaskIds.contains(t.getId()))
                .sorted(Comparator.comparingInt(t -> t.getPriority() == Priority.HIGH ? 0 : 1))
                .collect(Collectors.toList());

        for (Task task : preferredTodayTasks) {
            handledRecurringIds.add(task.getId());
            int needed = task.getTotalMinutes() != null ? task.getTotalMinutes() : 0;
            if (needed <= 0)
                continue;

            boolean canSchedule = Boolean.FALSE.equals(task.getSplittable())
                    ? canFitContiguously(slotPointers, reservedBySlot, needed)
                    : calculateAvailableMinutes(slotPointers, reservedBySlot) >= needed;

            DailyTaskAllocation allocation = new DailyTaskAllocation();
            allocation.setDayEntry(dayEntry);
            allocation.setTask(task);
            allocation.setPlannedMinutes(needed);
            allocation.setActualMinutes(0);
            allocation.setDone(null);

            if (!canSchedule) {
                if (task.getPriority() == Priority.HIGH) {
                    allocation.setConflict(true);
                    allocationRepository.save(allocation);

                    ConflictResponse conflict = new ConflictResponse();
                    conflict.setAllocationId(allocation.getId());
                    conflict.setTaskTitle(task.getTitle());
                    conflict.setDurationMinutes(needed);
                    conflict.setAvailableMinutes(calculateAvailableMinutes(slotPointers, reservedBySlot));
                    conflict.setReason("Needs " + needed + " min but not enough time available");
                    conflictResponses.add(conflict);
                }
                // LOW priority — skip silently
            } else {
                allocation.setConflict(false);
                allocationRepository.save(allocation);
                List<ScheduleResponse.SlotResponse> slotResponses = Boolean.FALSE.equals(task.getSplittable())
                        ? fillSlotsContiguously(allocation, needed, slotPointers, reservedBySlot)
                        : fillSlots(allocation, needed, slotPointers, reservedBySlot);

                ScheduleResponse.AllocationResponse ar = buildAllocationResponse(allocation, task, slotResponses);
                allocationResponses.add(ar);
            }
        }

        // Step 2: All other incomplete tasks (ONE_TIME + RECURRING not yet handled
        // today)
        List<Task> remainingTasks = taskRepository.findByCompletedFalse()
                .stream()
                .filter(t -> !protectedTaskIds.contains(t.getId()))
                .filter(t -> !handledRecurringIds.contains(t.getId()))
                .filter(t -> {
                    List<TaskDependency> deps = taskDependencyRepository.findByTaskId(t.getId());
                    return deps.stream()
                            .allMatch(d -> Boolean.TRUE.equals(d.getDependsOn().getCompleted()));
                })
                .sorted(Comparator.comparingInt((Task t) -> {
                    if (t.getType() == TaskType.ONE_TIME && t.getDdl() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(date, t.getDdl());
                        if (days < 0)
                            return -2;
                    }
                    if (t.getType() == TaskType.RECURRING && !isOnTrackForCycle(t, date))
                        return -1;
                    if (t.getType() == TaskType.ONE_TIME && t.getDdl() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(date, t.getDdl());
                        if (days <= 3)
                            return 0;
                    }
                    return t.getPriority() == Priority.HIGH ? 1 : 2;
                }))
                .collect(Collectors.toList());

        for (Task task : remainingTasks) {
            int minutesToSchedule;
            if (task.getType() == TaskType.RECURRING) {
                // For RECURRING tasks in step 2, only schedule if behind on cycle or forced by
                // carry-over
                if (!forceTaskIds.contains(task.getId()) && isOnTrackForCycle(task, date))
                    continue;
                minutesToSchedule = task.getTotalMinutes() != null ? task.getTotalMinutes() : 0;
            } else {
                minutesToSchedule = task.getTotalMinutes() - task.getConsumedMinutes();
            }

            if (minutesToSchedule <= 0)
                continue;

            int available = calculateAvailableMinutes(slotPointers, reservedBySlot);
            if (available <= 0)
                break;

            boolean splittable = !Boolean.FALSE.equals(task.getSplittable());

            if (!splittable) {
                if (!canFitContiguously(slotPointers, reservedBySlot, minutesToSchedule))
                    continue;
            } else {
                minutesToSchedule = Math.min(minutesToSchedule, available);
            }

            DailyTaskAllocation allocation = new DailyTaskAllocation();
            allocation.setDayEntry(dayEntry);
            allocation.setTask(task);
            allocation.setPlannedMinutes(minutesToSchedule);
            allocation.setActualMinutes(0);
            allocation.setDone(null);
            allocation.setConflict(false);
            allocationRepository.save(allocation);

            List<ScheduleResponse.SlotResponse> slotResponses = splittable
                    ? fillSlots(allocation, minutesToSchedule, slotPointers, reservedBySlot)
                    : fillSlotsContiguously(allocation, minutesToSchedule, slotPointers, reservedBySlot);

            allocationResponses.add(buildAllocationResponse(allocation, task, slotResponses));
        }

        // Prepend logged + carried-over allocations to response
        List<DailyTaskAllocation> preservedAllocations = new ArrayList<>(loggedAllocations);
        preservedAllocations.addAll(carriedOverAllocations);

        List<ScheduleResponse.AllocationResponse> preservedResponses = preservedAllocations.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getConflict()))
                .map(a -> {
                    ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
                    ar.setAllocationId(a.getId());
                    ar.setTaskTitle(a.getTask().getTitle());
                    ar.setTaskType(a.getTask().getType());
                    ar.setPlannedMinutes(a.getPlannedMinutes());
                    ar.setActualMinutes(a.getActualMinutes());
                    ar.setDone(a.getDone());
                    ar.setCarriedOver(a.isCarriedOver());
                    ar.setMemo(a.getMemo());
                    ar.setConsumedMinutes(a.getTask().getConsumedMinutes());
                    ar.setTotalMinutes(a.getTask().getTotalMinutes());
                    ar.setDdl(a.getTask().getDdl());
                    ar.setSlots(allocationSlotRepository.findByDailyTaskAllocationId(a.getId())
                            .stream()
                            .map(s -> {
                                ScheduleResponse.SlotResponse sr = new ScheduleResponse.SlotResponse();
                                sr.setStart(s.getStart());
                                sr.setEnd(s.getEnd());
                                sr.setMinutes(s.getMinutes());
                                return sr;
                            })
                            .collect(Collectors.toList()));
                    return ar;
                })
                .collect(Collectors.toList());
        preservedResponses.addAll(allocationResponses);

        ScheduleResponse response = new ScheduleResponse();
        response.setDate(date);
        response.setClosed(false);
        response.setAllocations(preservedResponses);
        response.setConflicts(conflictResponses);
        return response;
    }

    private void settleCycleDebt(LocalDate date) {
        List<Task> recurringTasks = taskRepository.findByCompletedFalse()
                .stream()
                .filter(t -> t.getType() == TaskType.RECURRING)
                .filter(t -> t.getCycleType() != null)
                .collect(Collectors.toList());

        for (Task task : recurringTasks) {
            LocalDate prevStart;
            LocalDate prevEnd;

            if (task.getCycleType() == CycleType.WEEKLY) {
                LocalDate thisWeekStart = date.with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                prevStart = thisWeekStart.minusWeeks(1);
                prevEnd = thisWeekStart.minusDays(1);
            } else {
                LocalDate thisMonthStart = date.withDayOfMonth(1);
                prevStart = thisMonthStart.minusMonths(1);
                prevEnd = thisMonthStart.minusDays(1);
            }

            long completedLastCycle = allocationRepository.findByTaskId(task.getId())
                    .stream()
                    .filter(a -> {
                        LocalDate d = a.getDayEntry().getDate();
                        return !d.isBefore(prevStart) && !d.isAfter(prevEnd);
                    })
                    .filter(a -> Boolean.TRUE.equals(a.getDone()))
                    .count();

            int required = task.getCycleCount() != null ? task.getCycleCount() : 0;
            int debt = (int) Math.max(required - completedLastCycle, 0);

            if (debt > 0) {
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

    private LocalTime skipReservedBlocks(LocalTime pointer, List<AllocationSlot> reserved) {
        boolean advanced;
        do {
            advanced = false;
            for (AllocationSlot as : reserved) {
                if (!as.getStart().isAfter(pointer) && as.getEnd().isAfter(pointer)) {
                    pointer = as.getEnd();
                    advanced = true;
                }
            }
        } while (advanced);
        return pointer;
    }

    private LocalTime nextReservedStart(LocalTime pointer, List<AllocationSlot> reserved) {
        return reserved.stream()
                .map(AllocationSlot::getStart)
                .filter(s -> s.isAfter(pointer))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private int calculateAvailableMinutes(Map<FreeSlot, LocalTime> slotPointers,
                                          Map<Long, List<AllocationSlot>> reservedBySlot) {
        int total = 0;
        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            FreeSlot slot = entry.getKey();
            List<AllocationSlot> reserved = reservedBySlot.getOrDefault(slot.getId(), Collections.emptyList());
            LocalTime pointer = skipReservedBlocks(entry.getValue(), reserved);
            while (pointer.isBefore(slot.getEnd())) {
                LocalTime segEnd = nextReservedStart(pointer, reserved);
                if (segEnd == null || segEnd.compareTo(slot.getEnd()) >= 0) {
                    total += (int) java.time.Duration.between(pointer, slot.getEnd()).toMinutes();
                    break;
                }
                total += (int) java.time.Duration.between(pointer, segEnd).toMinutes();
                pointer = skipReservedBlocks(segEnd, reserved);
            }
        }
        return total;
    }

    private boolean canFitContiguously(Map<FreeSlot, LocalTime> slotPointers,
                                       Map<Long, List<AllocationSlot>> reservedBySlot,
                                       int minutesNeeded) {
        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            FreeSlot slot = entry.getKey();
            List<AllocationSlot> reserved = reservedBySlot.getOrDefault(slot.getId(), Collections.emptyList());
            LocalTime pointer = skipReservedBlocks(entry.getValue(), reserved);
            while (pointer.isBefore(slot.getEnd())) {
                LocalTime segEnd = nextReservedStart(pointer, reserved);
                LocalTime effectiveEnd = (segEnd != null && segEnd.isBefore(slot.getEnd())) ? segEnd : slot.getEnd();
                int available = (int) java.time.Duration.between(pointer, effectiveEnd).toMinutes();
                if (available >= minutesNeeded) return true;
                pointer = skipReservedBlocks(effectiveEnd, reserved);
            }
        }
        return false;
    }

    private List<ScheduleResponse.SlotResponse> fillSlots(
            DailyTaskAllocation allocation,
            int minutesNeeded,
            Map<FreeSlot, LocalTime> slotPointers,
            Map<Long, List<AllocationSlot>> reservedBySlot) {

        List<ScheduleResponse.SlotResponse> slotResponses = new ArrayList<>();
        int remaining = minutesNeeded;

        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            if (remaining <= 0) break;

            FreeSlot slot = entry.getKey();
            List<AllocationSlot> reserved = reservedBySlot.getOrDefault(slot.getId(), Collections.emptyList());
            LocalTime pointer = skipReservedBlocks(entry.getValue(), reserved);

            while (remaining > 0 && pointer.isBefore(slot.getEnd())) {
                LocalTime segEnd = nextReservedStart(pointer, reserved);
                LocalTime effectiveEnd = (segEnd != null && segEnd.isBefore(slot.getEnd())) ? segEnd : slot.getEnd();

                int available = (int) java.time.Duration.between(pointer, effectiveEnd).toMinutes();
                if (available <= 0) { pointer = skipReservedBlocks(effectiveEnd, reserved); continue; }

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

                pointer = skipReservedBlocks(end, reserved);
                remaining -= use;
            }

            slotPointers.put(slot, pointer);
        }

        return slotResponses;
    }

    private List<ScheduleResponse.SlotResponse> fillSlotsContiguously(
            DailyTaskAllocation allocation,
            int minutesNeeded,
            Map<FreeSlot, LocalTime> slotPointers,
            Map<Long, List<AllocationSlot>> reservedBySlot) {

        for (Map.Entry<FreeSlot, LocalTime> entry : slotPointers.entrySet()) {
            FreeSlot slot = entry.getKey();
            List<AllocationSlot> reserved = reservedBySlot.getOrDefault(slot.getId(), Collections.emptyList());
            LocalTime pointer = skipReservedBlocks(entry.getValue(), reserved);

            while (pointer.isBefore(slot.getEnd())) {
                LocalTime segEnd = nextReservedStart(pointer, reserved);
                LocalTime effectiveEnd = (segEnd != null && segEnd.isBefore(slot.getEnd())) ? segEnd : slot.getEnd();

                int available = (int) java.time.Duration.between(pointer, effectiveEnd).toMinutes();
                if (available >= minutesNeeded) {
                    LocalTime end = pointer.plusMinutes(minutesNeeded);

                    AllocationSlot as = new AllocationSlot();
                    as.setDailyTaskAllocation(allocation);
                    as.setFreeSlot(slot);
                    as.setStart(pointer);
                    as.setEnd(end);
                    as.setMinutes(minutesNeeded);
                    allocationSlotRepository.save(as);

                    slotPointers.put(slot, end);

                    ScheduleResponse.SlotResponse sr = new ScheduleResponse.SlotResponse();
                    sr.setStart(pointer);
                    sr.setEnd(end);
                    sr.setMinutes(minutesNeeded);
                    return List.of(sr);
                }

                pointer = skipReservedBlocks(effectiveEnd, reserved);
            }
        }

        return List.of();
    }

    private boolean isOnTrackForCycle(Task task, LocalDate date) {
        if (task.getType() != TaskType.RECURRING || task.getCycleType() == null) {
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
                    LocalDate d = a.getDayEntry().getDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .filter(a -> Boolean.TRUE.equals(a.getDone()))
                .count();

        int daysLeft = (int) java.time.temporal.ChronoUnit.DAYS.between(date, end) + 1;
        int cycleDebt = task.getCycleDebt() != null ? task.getCycleDebt() : 0;
        int remaining = (task.getCycleCount() + cycleDebt) - (int) completedCount;

        return remaining <= daysLeft;
    }

    private ScheduleResponse.AllocationResponse buildAllocationResponse(
            DailyTaskAllocation allocation, Task task, List<ScheduleResponse.SlotResponse> slots) {
        ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
        ar.setAllocationId(allocation.getId());
        ar.setTaskTitle(task.getTitle());
        ar.setTaskType(task.getType());
        ar.setPlannedMinutes(allocation.getPlannedMinutes());
        ar.setActualMinutes(0);
        ar.setDone(null);
        ar.setCarriedOver(false);
        ar.setConsumedMinutes(task.getConsumedMinutes());
        ar.setTotalMinutes(task.getTotalMinutes());
        ar.setDdl(task.getDdl());
        ar.setSlots(slots);
        return ar;
    }

    public ScheduleResponse getSchedule(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        DayEntry dayEntry = dayEntryRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DayEntry not found: " + dateStr));

        List<DailyTaskAllocation> allocations = allocationRepository.findByDayEntryId(dayEntry.getId());

        List<ScheduleResponse.AllocationResponse> allocationResponses = allocations.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getConflict()))
                .map(a -> {
                    ScheduleResponse.AllocationResponse ar = new ScheduleResponse.AllocationResponse();
                    ar.setAllocationId(a.getId());
                    ar.setTaskTitle(a.getTask().getTitle());
                    ar.setTaskType(a.getTask().getType());
                    ar.setPlannedMinutes(a.getPlannedMinutes());
                    ar.setActualMinutes(a.getActualMinutes());
                    ar.setDone(a.getDone());
                    ar.setCarriedOver(a.isCarriedOver());
                    ar.setMemo(a.getMemo());
                    ar.setConsumedMinutes(a.getTask().getConsumedMinutes());
                    ar.setTotalMinutes(a.getTask().getTotalMinutes());
                    ar.setDdl(a.getTask().getDdl());
                    ar.setSlots(allocationSlotRepository.findByDailyTaskAllocationId(a.getId())
                            .stream()
                            .map(s -> {
                                ScheduleResponse.SlotResponse sr = new ScheduleResponse.SlotResponse();
                                sr.setStart(s.getStart());
                                sr.setEnd(s.getEnd());
                                sr.setMinutes(s.getMinutes());
                                return sr;
                            })
                            .collect(Collectors.toList()));
                    return ar;
                })
                .collect(Collectors.toList());

        List<ConflictResponse> conflictResponses = allocations.stream()
                .filter(a -> Boolean.TRUE.equals(a.getConflict()))
                .map(a -> {
                    ConflictResponse cr = new ConflictResponse();
                    cr.setAllocationId(a.getId());
                    cr.setTaskTitle(a.getTask().getTitle());
                    cr.setDurationMinutes(a.getPlannedMinutes());
                    cr.setReason("Insufficient time");
                    return cr;
                })
                .collect(Collectors.toList());

        ScheduleResponse response = new ScheduleResponse();
        response.setDate(date);
        response.setClosed(dayEntry.isClosed());
        response.setAllocations(allocationResponses);
        response.setConflicts(conflictResponses);
        return response;
    }
}
