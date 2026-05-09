package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByType(TaskType type);

    List<Task> findByCompletedFalse();
}