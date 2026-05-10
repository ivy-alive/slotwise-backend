package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.TaskRequest;
import com.slotwise.slotwise.dto.response.TaskResponse;
import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.repository.DailyTaskAllocationRepository;
import com.slotwise.slotwise.repository.TaskDependencyRepository;
import com.slotwise.slotwise.repository.TaskPreferredDayRepository;
import com.slotwise.slotwise.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskPreferredDayRepository preferredDayRepository;

    @Mock
    private DailyTaskAllocationRepository allocationRepository;

    @Mock
    private TaskDependencyRepository taskDependencyRepository;

    @InjectMocks
    private TaskService taskService;

    private Task oneTimeTask;
    private Task recurringTask;

    @BeforeEach
    void setUp() {
        oneTimeTask = new Task();
        oneTimeTask.setId(1L);
        oneTimeTask.setTitle("Read Spring Docs");
        oneTimeTask.setType(TaskType.ONE_TIME);
        oneTimeTask.setPriority(Priority.HIGH);
        oneTimeTask.setTotalMinutes(180);
        oneTimeTask.setConsumedMinutes(0);
        oneTimeTask.setCompleted(false);
        oneTimeTask.setSplittable(true);

        recurringTask = new Task();
        recurringTask.setId(2L);
        recurringTask.setTitle("Morning Run");
        recurringTask.setType(TaskType.RECURRING);
        recurringTask.setPriority(Priority.HIGH);
        recurringTask.setTotalMinutes(45);
        recurringTask.setConsumedMinutes(0);
        recurringTask.setCompleted(false);
        recurringTask.setSplittable(false);
        recurringTask.setCycleType(CycleType.WEEKLY);
        recurringTask.setCycleCount(3);
    }

    @Test
    void createTask_oneTime_shouldSetConsumedMinutesToZeroAndRemainingToTotal() {
        TaskRequest request = new TaskRequest();
        request.setTitle("Read Spring Docs");
        request.setType(TaskType.ONE_TIME);
        request.setPriority(Priority.HIGH);
        request.setTotalMinutes(180);
        request.setSplittable(true);

        when(taskRepository.save(any(Task.class))).thenReturn(oneTimeTask);
        when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of());
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());

        TaskResponse response = taskService.createTask(request);

        assertEquals("Read Spring Docs", response.getTitle());
        assertEquals(180, response.getRemainingMinutes());
        assertEquals(TaskType.ONE_TIME, response.getType());
        assertFalse(response.getCompleted());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void createTask_recurring_shouldSavePreferredDays() {
        TaskRequest request = new TaskRequest();
        request.setTitle("Morning Run");
        request.setType(TaskType.RECURRING);
        request.setPriority(Priority.HIGH);
        request.setTotalMinutes(45);
        request.setSplittable(false);
        request.setCycleType(CycleType.WEEKLY);
        request.setCycleCount(3);
        request.setPreferredDays(List.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY));

        when(taskRepository.save(any(Task.class))).thenReturn(recurringTask);
        when(preferredDayRepository.findByTaskId(2L)).thenReturn(List.of());
        when(taskDependencyRepository.findByTaskId(2L)).thenReturn(List.of());
        when(allocationRepository.findByTaskId(2L)).thenReturn(List.of());

        TaskResponse response = taskService.createTask(request);

        assertEquals("Morning Run", response.getTitle());
        assertEquals(TaskType.RECURRING, response.getType());
        verify(preferredDayRepository, times(2)).save(any());
    }

    @Test
    void getTaskById_shouldThrowException_whenTaskNotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTaskById(999L));
    }

    @Test
    void getTaskById_shouldReturnTask_whenTaskExists() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(oneTimeTask));
        when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of());
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());

        TaskResponse response = taskService.getTaskById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Read Spring Docs", response.getTitle());
        assertEquals(TaskType.ONE_TIME, response.getType());
    }

    @Test
    void deleteTask_shouldDeleteAllocationsAndPreferredDays() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(oneTimeTask));
        when(allocationRepository.findByTaskId(1L)).thenReturn(List.of());
        when(preferredDayRepository.findByTaskId(1L)).thenReturn(List.of());
        when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of());
        when(taskDependencyRepository.findByDependsOnId(1L)).thenReturn(List.of());

        taskService.deleteTask(1L);

        verify(taskRepository).delete(oneTimeTask);
        verify(allocationRepository).deleteAll(any());
        verify(preferredDayRepository).deleteAll(any());
    }
}
