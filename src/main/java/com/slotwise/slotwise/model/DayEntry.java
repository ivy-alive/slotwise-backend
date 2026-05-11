package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Entity
@Table(name = "day_entries")
public class DayEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    private boolean closed = false;

    @OneToMany(mappedBy = "dayEntry", cascade = CascadeType.ALL)
    private List<FreeSlot> freeSlots;

    @OneToMany(mappedBy = "dayEntry", cascade = CascadeType.ALL)
    private List<DailyTaskAllocation> allocations;
}