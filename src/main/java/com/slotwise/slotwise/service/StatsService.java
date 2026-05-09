package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.response.WorkoutStatsResponse;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.model.DailyTaskAllocation;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.model.WorkoutScheduledDay;
import com.slotwise.slotwise.repository.DailyTaskAllocationRepository;
import com.slotwise.slotwise.repository.TaskRepository;
import com.slotwise.slotwise.repository.WorkoutScheduledDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final TaskRepository taskRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final WorkoutScheduledDayRepository workoutScheduledDayRepository;

    public WorkoutStatsResponse getWeeklyWorkoutStats(String week) {
        // week format: 2026-W18
        int year = Integer.parseInt(week.split("-W")[0]);
        int weekNum = Integer.parseInt(week.split("-W")[1]);
        LocalDate startOfWeek = LocalDate.ofYearDay(year, 1)
                .with(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNum)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        return buildStats(week, startOfWeek, endOfWeek);
    }

    public WorkoutStatsResponse getMonthlyWorkoutStats(String month) {
        // month format: 2026-05
        LocalDate start = LocalDate.parse(month + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        return buildStats(month, start, end);
    }

    private WorkoutStatsResponse buildStats(String period, LocalDate start, LocalDate end) {
        List<Task> workoutTasks = taskRepository.findByType(TaskType.WORKOUT);

        List<WorkoutStatsResponse.WorkoutTaskStats> statsList = new ArrayList<>();

        for (Task task : workoutTasks) {
            List<DailyTaskAllocation> allocations = allocationRepository.findByTaskId(task.getId())
                    .stream()
                    .filter(a -> {
                        LocalDate date = a.getDayEntry().getDate();
                        return !date.isBefore(start) && !date.isAfter(end);
                    })
                    .collect(Collectors.toList());

            List<DayOfWeek> scheduledDays = workoutScheduledDayRepository.findAll()
                    .stream()
                    .filter(wsd -> wsd.getTask().getId().equals(task.getId()))
                    .map(WorkoutScheduledDay::getDayOfWeek)
                    .collect(Collectors.toList());

            List<WorkoutStatsResponse.AllocationSummary> summaries = allocations.stream()
                    .map(a -> {
                        WorkoutStatsResponse.AllocationSummary summary = new WorkoutStatsResponse.AllocationSummary();
                        summary.setDate(a.getDayEntry().getDate());
                        summary.setDone(a.getDone() != null && a.getDone());
                        return summary;
                    })
                    .collect(Collectors.toList());

            WorkoutStatsResponse.WorkoutTaskStats stats = new WorkoutStatsResponse.WorkoutTaskStats();
            stats.setTaskId(task.getId());
            stats.setTitle(task.getTitle());
            stats.setScheduledDays(scheduledDays);
            stats.setPlannedCount(allocations.size());
            stats.setActualCount((int) allocations.stream()
                    .filter(a -> a.getDone() != null && a.getDone()).count());
            stats.setAllocations(summaries);

            statsList.add(stats);
        }

        WorkoutStatsResponse response = new WorkoutStatsResponse();
        response.setPeriod(period);
        response.setWorkoutStats(statsList);
        return response;
    }
}