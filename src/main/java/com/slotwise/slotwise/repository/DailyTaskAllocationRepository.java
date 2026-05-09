package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.DailyTaskAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyTaskAllocationRepository extends JpaRepository<DailyTaskAllocation, Long> {
    List<DailyTaskAllocation> findByDayEntryId(Long dayEntryId);

    List<DailyTaskAllocation> findByTaskId(Long taskId);

    List<DailyTaskAllocation> findByDayEntryIdAndConflictTrue(Long dayEntryId);
}