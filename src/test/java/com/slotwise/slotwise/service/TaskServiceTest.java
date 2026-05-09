package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.StudyTaskRequest;
import com.slotwise.slotwise.dto.response.TaskResponse;
import com.slotwise.slotwise.enums.CycleType;
import com.slotwise.slotwise.enums.Priority;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.repository.TaskRepository;
import com.slotwise.slotwise.repository.WorkoutScheduledDayRepository;
import com.slotwise.slotwise.repository.TaskDependencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkoutScheduledDayRepository workoutScheduledDayRepository;

    @Mock
    private TaskDependencyRepository taskDependencyRepository;

    @InjectMocks
    private TaskService taskService;

    private Task studyTask;

    @BeforeEach
    void setUp() {
        studyTask = new Task();
        studyTask.setId(1L);
        studyTask.setTitle("Read Spring Docs");
        studyTask.setType(TaskType.STUDY);
        studyTask.setPriority(Priority.HIGH);
        studyTask.setTotalMinutes(180);
        studyTask.setRemainingMinutes(180);
        studyTask.setCompleted(false);
        studyTask.setCycleType(CycleType.WEEKLY);
        studyTask.setCycleCount(3);
    }

    @Test
    void createStudyTask_shouldSetRemainingMinutesToTotal() {
        // Arrange
        StudyTaskRequest request = new StudyTaskRequest();
        request.setTitle("Read Spring Docs");
        request.setPriority(Priority.HIGH);
        request.setTotalMinutes(180);
        request.setCycleType(CycleType.WEEKLY);
        request.setCycleCount(3);

        when(taskRepository.save(any(Task.class))).thenReturn(studyTask);

        // Act
        TaskResponse response = taskService.createStudyTask(request);

        // Assert
        assertEquals("Read Spring Docs", response.getTitle());
        assertEquals(180, response.getRemainingMinutes());
        assertEquals(TaskType.STUDY, response.getType());
        assertFalse(response.getCompleted());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void getTaskById_shouldThrowException_whenTaskNotFound() {
        // Arrange
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> taskService.getTaskById(999L));
    }

    @Test
    void getTaskById_shouldReturnTask_whenTaskExists() {
        // Arrange
        when(taskRepository.findById(1L)).thenReturn(Optional.of(studyTask));

        // Act
        TaskResponse response = taskService.getTaskById(1L);

        // Assert
        assertEquals(1L, response.getId());
        assertEquals("Read Spring Docs", response.getTitle());
    }
}