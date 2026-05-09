package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.AllocationSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllocationSlotRepository extends JpaRepository<AllocationSlot, Long> {
    List<AllocationSlot> findByDailyTaskAllocationId(Long allocationId);

    List<AllocationSlot> findByFreeSlotId(Long freeSlotId);
}