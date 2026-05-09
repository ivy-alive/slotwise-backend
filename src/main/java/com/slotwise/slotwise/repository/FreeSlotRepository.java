package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.FreeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FreeSlotRepository extends JpaRepository<FreeSlot, Long> {
    List<FreeSlot> findByDayEntryIdOrderByStart(Long dayEntryId);
}