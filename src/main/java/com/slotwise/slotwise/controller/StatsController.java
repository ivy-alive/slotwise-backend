package com.slotwise.slotwise.controller;

import com.slotwise.slotwise.dto.response.WorkoutStatsResponse;
import com.slotwise.slotwise.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/workout/weekly")
    public WorkoutStatsResponse getWeeklyStats(@RequestParam String week) {
        return statsService.getWeeklyWorkoutStats(week);
    }

    @GetMapping("/workout/monthly")
    public WorkoutStatsResponse getMonthlyStats(@RequestParam String month) {
        return statsService.getMonthlyWorkoutStats(month);
    }
}