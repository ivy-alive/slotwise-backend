package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.response.ScheduleResponse;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.model.*;
import com.slotwise.slotwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock
    private DayEntryRepository dayEntryRepository;
    @Mock
    private FreeSlotRepository freeSlotRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private WorkoutScheduledDayRepository workoutScheduledDayRepository;
    @Mock
    private DailyTaskAllocationRepository allocationRepository;
    @Mock
    private AllocationSlotRepository allocationSlotRepository;

    @Mock
    private TaskDependencyRepository taskDependencyRepository;

    @InjectMocks
    private SchedulingService schedulingService;

    private DayEntry dayEntry;
    private FreeSlot freeSlot;

    @BeforeEach
    void setUp() {
        dayEntry = new DayEntry();
        dayEntry.setId(1L);
        dayEntry.setDate(LocalDate.of(2026, 4, 28)); // Tuesday

        freeSlot = new FreeSlot();
        freeSlot.setId(1L);
        freeSlot.setDayEntry(dayEntry);
        freeSlot.setStart(LocalTime.of(9, 0));
        freeSlot.setEnd(LocalTime.of(11, 0)); // 120 minutes
    }

    @Test
    void schedule_shouldAssignWorkoutTask_whenEnoughTime() {
        // Arrange
        Task workoutTask = new Task();
        workoutTask.setId(1L);
        workoutTask.setTitle("Running");
        workoutTask.setType(TaskType.WORKOUT);
        workoutTask.setPriority(Priority.HIGH);
        workoutTask.setDurationMinutes(45);

        WorkoutScheduledDay wsd = new WorkoutScheduledDay();
        wsd.setTask(workoutTask);
        wsd.setDayOfWeek(DayOfWeek.TUESDAY);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot));
        when(workoutScheduledDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of(wsd));
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of());
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        // Assert
        assertEquals(1, response.getAllocations().size());
        assertEquals("Running", response.getAllocations().get(0).getTaskTitle());
        assertEquals(45, response.getAllocations().get(0).getPlannedMinutes());
        assertTrue(response.getConflicts().isEmpty());
    }

    @Test
    void schedule_shouldMarkConflict_whenNotEnoughTime() {
        // Arrange
        Task workoutTask = new Task();
        workoutTask.setId(1L);
        workoutTask.setTitle("Long Run");
        workoutTask.setType(TaskType.WORKOUT);
        workoutTask.setPriority(Priority.HIGH);
        workoutTask.setDurationMinutes(200); // Over 120 minutes slot

        WorkoutScheduledDay wsd = new WorkoutScheduledDay();
        wsd.setTask(workoutTask);
        wsd.setDayOfWeek(DayOfWeek.TUESDAY);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot));
        when(workoutScheduledDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of(wsd));
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of());
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        // Assert
        assertTrue(response.getAllocations().isEmpty());
        assertEquals(1, response.getConflicts().size());
        assertEquals("Long Run", response.getConflicts().get(0).getTaskTitle());
    }

    @Test
    void schedule_shouldSplitStudyTask_acrossMultipleFreeSlots() {
        // Arrange
        FreeSlot freeSlot2 = new FreeSlot();
        freeSlot2.setId(2L);
        freeSlot2.setDayEntry(dayEntry);
        freeSlot2.setStart(LocalTime.of(20, 0));
        freeSlot2.setEnd(LocalTime.of(22, 0)); // 120 minutes

        Task studyTask = new Task();
        studyTask.setId(2L);
        studyTask.setTitle("Read Spring Docs");
        studyTask.setType(TaskType.STUDY);
        studyTask.setPriority(Priority.HIGH);
        studyTask.setRemainingMinutes(180); // Need 180 minutes, spanning two slots

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot, freeSlot2));
        when(workoutScheduledDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of());
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(studyTask));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        // Assert
        assertEquals(1, response.getAllocations().size());
        assertEquals(180, response.getAllocations().get(0).getPlannedMinutes());
        assertEquals(2, response.getAllocations().get(0).getSlots().size()); // Spanning two slots
    }
}