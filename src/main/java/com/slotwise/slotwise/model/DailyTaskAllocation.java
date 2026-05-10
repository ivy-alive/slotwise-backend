package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "daily_task_allocations")
public class DailyTaskAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_entry_id")
    private DayEntry dayEntry;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    private Integer plannedMinutes;
    private Integer actualMinutes;
    private Boolean done;
    private Boolean conflict;

    @Column(length = 1000)
    private String memo;

    @OneToMany(mappedBy = "dailyTaskAllocation", cascade = CascadeType.ALL)
    private List<AllocationSlot> allocationSlots;
}