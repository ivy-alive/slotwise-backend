package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Data
@Entity
@Table(name = "allocation_slots")
public class AllocationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "daily_task_allocation_id")
    private DailyTaskAllocation dailyTaskAllocation;

    @ManyToOne
    @JoinColumn(name = "free_slot_id")
    private FreeSlot freeSlot;

    private LocalTime start;
    private LocalTime end;
    private Integer minutes;
}