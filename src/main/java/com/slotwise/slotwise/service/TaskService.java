package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.StudyTaskRequest;
import com.slotwise.slotwise.dto.request.WorkoutTaskRequest;
import com.slotwise.slotwise.dto.response.TaskResponse;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.DailyTaskAllocation;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.model.TaskDependency;
import com.slotwise.slotwise.model.WorkoutScheduledDay;
import com.slotwise.slotwise.repository.DailyTaskAllocationRepository;
import com.slotwise.slotwise.repository.TaskDependencyRepository;
import com.slotwise.slotwise.repository.TaskRepository;
import com.slotwise.slotwise.repository.WorkoutScheduledDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final WorkoutScheduledDayRepository workoutScheduledDayRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskResponse createStudyTask(StudyTaskRequest request) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setType(TaskType.STUDY);
        task.setPriority(request.getPriority());
        task.setTotalMinutes(request.getTotalMinutes());
        task.setRemainingMinutes(request.getTotalMinutes());
        task.setCompleted(false);
        task.setCycleType(request.getCycleType());
        task.setCycleCount(request.getCycleCount());
        task.setPreferredDay(request.getPreferredDay());
        task.setPreferredTime(request.getPreferredTime());
        task.setDueDate(request.getDueDate());
        taskRepository.save(task);
        if (request.getDependsOnIds() != null) {
            for (Long dependsOnId : request.getDependsOnIds()) {
                Task dependsOn = taskRepository.findById(dependsOnId)
                        .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + dependsOnId));
                TaskDependency dependency = new TaskDependency();
                dependency.setTask(task);
                dependency.setDependsOn(dependsOn);
                taskDependencyRepository.save(dependency);
            }
        }
        return toResponse(task);
    }

    public TaskResponse createWorkoutTask(WorkoutTaskRequest request) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setType(TaskType.WORKOUT);
        task.setPriority(request.getPriority());
        task.setDurationMinutes(request.getDurationMinutes());
        taskRepository.save(task);

        if (request.getScheduledDays() != null) {
            for (var day : request.getScheduledDays()) {
                WorkoutScheduledDay wsd = new WorkoutScheduledDay();
                wsd.setTask(task);
                wsd.setDayOfWeek(day);
                workoutScheduledDayRepository.save(wsd);
            }
        }

        return toResponse(task);
    }

    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        return toResponse(task);
    }

    public TaskResponse updateStudyTask(Long id, StudyTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        task.setTitle(request.getTitle());
        task.setPriority(request.getPriority());
        task.setTotalMinutes(request.getTotalMinutes());
        task.setCycleType(request.getCycleType());
        task.setCycleCount(request.getCycleCount());
        task.setPreferredDay(request.getPreferredDay());
        task.setPreferredTime(request.getPreferredTime());
        task.setDueDate(request.getDueDate());
        taskRepository.save(task);
        return toResponse(task);
    }

    public TaskResponse updateWorkoutTask(Long id, WorkoutTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        task.setTitle(request.getTitle());
        task.setPriority(request.getPriority());
        task.setDurationMinutes(request.getDurationMinutes());

        // delete old scheduledDays and rewrite with new ones
        List<WorkoutScheduledDay> existing = workoutScheduledDayRepository.findAll()
                .stream()
                .filter(wsd -> wsd.getTask().getId().equals(id))
                .collect(java.util.stream.Collectors.toList());
        workoutScheduledDayRepository.deleteAll(existing);

        if (request.getScheduledDays() != null) {
            for (var day : request.getScheduledDays()) {
                WorkoutScheduledDay wsd = new WorkoutScheduledDay();
                wsd.setTask(task);
                wsd.setDayOfWeek(day);
                workoutScheduledDayRepository.save(wsd);
            }
        }
        taskRepository.save(task);
        return toResponse(task);
    }

    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        // delete associated allocations first to avoid foreign key constraint issues
        List<DailyTaskAllocation> allocations = allocationRepository.findByTaskId(id);
        allocationRepository.deleteAll(allocations);

        // then delete WorkoutScheduledDays
        List<WorkoutScheduledDay> wsds = workoutScheduledDayRepository.findAll()
                .stream()
                .filter(wsd -> wsd.getTask().getId().equals(id))
                .collect(java.util.stream.Collectors.toList());
        workoutScheduledDayRepository.deleteAll(wsds);

        // Delete dependencies where this task is involved
        taskDependencyRepository.deleteAll(taskDependencyRepository.findByTaskId(id));
        taskDependencyRepository.deleteAll(taskDependencyRepository.findByDependsOnId(id));

        taskRepository.delete(task);
    }

    public void addDependency(Long taskId, Long dependsOnId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        Task dependsOn = taskRepository.findById(dependsOnId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + dependsOnId));

        TaskDependency dependency = new TaskDependency();
        dependency.setTask(task);
        dependency.setDependsOn(dependsOn);
        taskDependencyRepository.save(dependency);
    }

    public void removeDependency(Long taskId, Long dependsOnId) {
        List<TaskDependency> dependencies = taskDependencyRepository.findByTaskId(taskId);
        dependencies.stream()
                .filter(d -> d.getDependsOn().getId().equals(dependsOnId))
                .forEach(taskDependencyRepository::delete);
    }

    public List<TaskResponse> getDependencies(Long taskId) {
        return taskDependencyRepository.findByTaskId(taskId)
                .stream()
                .map(d -> toResponse(d.getDependsOn()))
                .collect(java.util.stream.Collectors.toList());
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setType(task.getType());
        response.setPriority(task.getPriority());
        response.setTotalMinutes(task.getTotalMinutes());
        response.setRemainingMinutes(task.getRemainingMinutes());
        response.setCompleted(task.getCompleted());
        response.setCycleType(task.getCycleType());
        response.setCycleCount(task.getCycleCount());
        response.setPreferredDay(task.getPreferredDay());
        response.setPreferredTime(task.getPreferredTime());
        response.setDurationMinutes(task.getDurationMinutes());

        if (task.getType() == TaskType.WORKOUT) {
            List<java.time.DayOfWeek> scheduledDays = workoutScheduledDayRepository
                    .findAll()
                    .stream()
                    .filter(wsd -> wsd.getTask().getId().equals(task.getId()))
                    .map(WorkoutScheduledDay::getDayOfWeek)
                    .collect(Collectors.toList());
            response.setScheduledDays(scheduledDays);
        }

        if (task.getType() == TaskType.STUDY) {
            List<TaskResponse> deps = taskDependencyRepository.findByTaskId(task.getId())
                    .stream()
                    .map(d -> {
                        TaskResponse dep = new TaskResponse();
                        dep.setId(d.getDependsOn().getId());
                        dep.setTitle(d.getDependsOn().getTitle());
                        return dep;
                    })
                    .collect(java.util.stream.Collectors.toList());
            response.setDependencies(deps);
        }

        return response;
    }
}