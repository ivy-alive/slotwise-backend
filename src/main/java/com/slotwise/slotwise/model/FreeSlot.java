package com.slotwise.slotwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Data
@Entity
@Table(name = "free_slots")
public class FreeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_entry_id")
    private DayEntry dayEntry;

    private LocalTime start;
    private LocalTime end;
}