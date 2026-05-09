package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.response.ScheduleResponse;
import com.slotwise.slotwise.enums.CycleType;
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

    @Mock private DayEntryRepository dayEntryRepository;
    @Mock private FreeSlotRepository freeSlotRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskPreferredDayRepository preferredDayRepository;
    @Mock private DailyTaskAllocationRepository allocationRepository;
    @Mock private AllocationSlotRepository allocationSlotRepository;
    @Mock private TaskDependencyRepository taskDependencyRepository;

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
    void schedule_shouldAssignRecurringTask_whenPreferredDayMatchesToday() {
        Task recurringTask = new Task();
        recurringTask.setId(1L);
        recurringTask.setTitle("Morning Run");
        recurringTask.setType(TaskType.RECURRING);
        recurringTask.setPriority(Priority.HIGH);
        recurringTask.setTotalMinutes(45);
        recurringTask.setSplittable(false);
        recurringTask.setCycleType(CycleType.WEEKLY);
        recurringTask.setCycleCount(3);
        recurringTask.setCycleDebt(0);

        TaskPreferredDay tpd = new TaskPreferredDay();
        tpd.setTask(recurringTask);
        tpd.setDayOfWeek(DayOfWeek.TUESDAY);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot));
        // settleCycleDebt: finds recurring tasks and checks their allocations
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(recurringTask));
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());
        // step 1: preferred day match
        when(preferredDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of(tpd));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        assertEquals(1, response.getAllocations().size());
        assertEquals("Morning Run", response.getAllocations().get(0).getTaskTitle());
        assertEquals(45, response.getAllocations().get(0).getPlannedMinutes());
        assertTrue(response.getConflicts().isEmpty());
    }

    @Test
    void schedule_shouldMarkConflict_whenMustRecurringTaskDoesNotFit() {
        Task recurringTask = new Task();
        recurringTask.setId(1L);
        recurringTask.setTitle("Long Run");
        recurringTask.setType(TaskType.RECURRING);
        recurringTask.setPriority(Priority.HIGH);
        recurringTask.setTotalMinutes(200); // more than 120 min available
        recurringTask.setSplittable(false);
        recurringTask.setCycleType(CycleType.WEEKLY);
        recurringTask.setCycleCount(3);
        recurringTask.setCycleDebt(0);

        TaskPreferredDay tpd = new TaskPreferredDay();
        tpd.setTask(recurringTask);
        tpd.setDayOfWeek(DayOfWeek.TUESDAY);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot));
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(recurringTask));
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());
        when(preferredDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of(tpd));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        assertTrue(response.getAllocations().isEmpty());
        assertEquals(1, response.getConflicts().size());
        assertEquals("Long Run", response.getConflicts().get(0).getTaskTitle());
    }

    @Test
    void schedule_shouldSkipOptionalRecurringTask_whenNotEnoughTime() {
        Task recurringTask = new Task();
        recurringTask.setId(1L);
        recurringTask.setTitle("Yoga");
        recurringTask.setType(TaskType.RECURRING);
        recurringTask.setPriority(Priority.LOW); // Optional
        recurringTask.setTotalMinutes(200);
        recurringTask.setSplittable(false);
        recurringTask.setCycleType(CycleType.WEEKLY);
        recurringTask.setCycleCount(3);
        recurringTask.setCycleDebt(0);

        TaskPreferredDay tpd = new TaskPreferredDay();
        tpd.setTask(recurringTask);
        tpd.setDayOfWeek(DayOfWeek.TUESDAY);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot));
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(recurringTask));
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());
        when(preferredDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of(tpd));

        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        assertTrue(response.getAllocations().isEmpty());
        assertTrue(response.getConflicts().isEmpty());
    }

    @Test
    void schedule_shouldSplitOneTimeTask_acrossMultipleFreeSlots() {
        FreeSlot freeSlot2 = new FreeSlot();
        freeSlot2.setId(2L);
        freeSlot2.setDayEntry(dayEntry);
        freeSlot2.setStart(LocalTime.of(20, 0));
        freeSlot2.setEnd(LocalTime.of(22, 0)); // 120 minutes

        Task oneTimeTask = new Task();
        oneTimeTask.setId(2L);
        oneTimeTask.setTitle("Read Spring Docs");
        oneTimeTask.setType(TaskType.ONE_TIME);
        oneTimeTask.setPriority(Priority.HIGH);
        oneTimeTask.setTotalMinutes(180);
        oneTimeTask.setRemainingMinutes(180);
        oneTimeTask.setSplittable(true); // can split across slots
        oneTimeTask.setCompleted(false);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(freeSlot, freeSlot2));
        // settleCycleDebt: no recurring tasks
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(oneTimeTask));
        when(preferredDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of());
        when(taskDependencyRepository.findByTaskId(2L)).thenReturn(List.of());
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        assertEquals(1, response.getAllocations().size());
        assertEquals(180, response.getAllocations().get(0).getPlannedMinutes());
        assertEquals(2, response.getAllocations().get(0).getSlots().size());
    }

    @Test
    void schedule_shouldNotSplitTask_whenSplittableIsFalse() {
        // Two slots of 60 min each — neither alone fits 90 min
        FreeSlot slot1 = new FreeSlot();
        slot1.setId(1L);
        slot1.setDayEntry(dayEntry);
        slot1.setStart(LocalTime.of(9, 0));
        slot1.setEnd(LocalTime.of(10, 0)); // 60 min

        FreeSlot slot2 = new FreeSlot();
        slot2.setId(2L);
        slot2.setDayEntry(dayEntry);
        slot2.setStart(LocalTime.of(14, 0));
        slot2.setEnd(LocalTime.of(15, 0)); // 60 min

        Task oneTimeTask = new Task();
        oneTimeTask.setId(3L);
        oneTimeTask.setTitle("Focused Work");
        oneTimeTask.setType(TaskType.ONE_TIME);
        oneTimeTask.setPriority(Priority.HIGH);
        oneTimeTask.setTotalMinutes(90);
        oneTimeTask.setRemainingMinutes(90);
        oneTimeTask.setSplittable(false); // must fit in one block
        oneTimeTask.setCompleted(false);

        when(dayEntryRepository.findByDate(any())).thenReturn(Optional.of(dayEntry));
        when(allocationRepository.findByDayEntryId(1L)).thenReturn(List.of());
        when(freeSlotRepository.findByDayEntryIdOrderByStart(1L)).thenReturn(List.of(slot1, slot2));
        when(taskRepository.findByCompletedFalse()).thenReturn(List.of(oneTimeTask));
        when(preferredDayRepository.findByDayOfWeek(DayOfWeek.TUESDAY)).thenReturn(List.of());
        when(taskDependencyRepository.findByTaskId(3L)).thenReturn(List.of());

        ScheduleResponse response = schedulingService.schedule("2026-04-28");

        // Task should not be scheduled since no single slot has 90 min
        assertTrue(response.getAllocations().isEmpty());
        assertTrue(response.getConflicts().isEmpty());
    }
}
