package com.slotwise.slotwise.repository;

import com.slotwise.slotwise.model.DayEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DayEntryRepository extends JpaRepository<DayEntry, Long> {
    Optional<DayEntry> findByDate(LocalDate date);
}