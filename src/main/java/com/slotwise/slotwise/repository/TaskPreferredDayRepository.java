package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.TaskPreferredDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface TaskPreferredDayRepository extends JpaRepository<TaskPreferredDay, Long> {
    List<TaskPreferredDay> findByDayOfWeek(DayOfWeek dayOfWeek);
    List<TaskPreferredDay> findByTaskId(Long taskId);
}
