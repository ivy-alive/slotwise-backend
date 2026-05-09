package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByTaskId(Long taskId);

    List<TaskDependency> findByDependsOnId(Long dependsOnId);
}