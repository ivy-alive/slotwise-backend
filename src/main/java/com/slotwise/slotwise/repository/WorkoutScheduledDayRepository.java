package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.WorkoutScheduledDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface WorkoutScheduledDayRepository extends JpaRepository<WorkoutScheduledDay, Long> {
    List<WorkoutScheduledDay> findByDayOfWeek(DayOfWeek dayOfWeek);
}