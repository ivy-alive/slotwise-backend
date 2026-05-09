package com.slotwise.slotwise.service;

import com.slotwise.slotwise.dto.request.TaskRequest;
import com.slotwise.slotwise.dto.response.TaskResponse;
import com.slotwise.slotwise.enums.TaskType;
import com.slotwise.slotwise.exception.ResourceNotFoundException;
import com.slotwise.slotwise.model.DailyTaskAllocation;
import com.slotwise.slotwise.model.Task;
import com.slotwise.slotwise.model.TaskDependency;
import com.slotwise.slotwise.model.TaskPreferredDay;
import com.slotwise.slotwise.repository.DailyTaskAllocationRepository;
import com.slotwise.slotwise.repository.TaskDependencyRepository;
import com.slotwise.slotwise.repository.TaskPreferredDayRepository;
import com.slotwise.slotwise.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskPreferredDayRepository preferredDayRepository;
    private final DailyTaskAllocationRepository allocationRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskResponse createTask(TaskRequest request) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setType(request.getType());
        task.setPriority(request.getPriority());
        task.setTotalMinutes(request.getTotalMinutes());
        task.setRemainingMinutes(request.getTotalMinutes());
        task.setSplittable(request.getSplittable());
        task.setCompleted(false);

        if (request.getType() == TaskType.ONE_TIME) {
            task.setDdl(request.getDdl());
        } else {
            task.setCycleType(request.getCycleType());
            task.setCycleCount(request.getCycleCount());
            task.setCycleDebt(0);
        }

        task = taskRepository.save(task);

        savePreferredDays(task, request.getPreferredDays());
        saveDependencies(task, request.getDependsOnIds());

        return toResponse(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        task.setTitle(request.getTitle());
        task.setPriority(request.getPriority());
        task.setTotalMinutes(request.getTotalMinutes());
        task.setSplittable(request.getSplittable());

        if (task.getType() == TaskType.ONE_TIME) {
            task.setDdl(request.getDdl());
        } else {
            task.setCycleType(request.getCycleType());
            task.setCycleCount(request.getCycleCount());
        }

        taskRepository.save(task);

        // Replace preferred days
        List<TaskPreferredDay> existing = preferredDayRepository.findByTaskId(id);
        preferredDayRepository.deleteAll(existing);
        savePreferredDays(task, request.getPreferredDays());

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

    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        List<DailyTaskAllocation> allocations = allocationRepository.findByTaskId(id);
        allocationRepository.deleteAll(allocations);

        List<TaskPreferredDay> preferredDays = preferredDayRepository.findByTaskId(id);
        preferredDayRepository.deleteAll(preferredDays);

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
        taskDependencyRepository.findByTaskId(taskId)
                .stream()
                .filter(d -> d.getDependsOn().getId().equals(dependsOnId))
                .forEach(taskDependencyRepository::delete);
    }

    public List<TaskResponse> getDependencies(Long taskId) {
        return taskDependencyRepository.findByTaskId(taskId)
                .stream()
                .map(d -> toResponse(d.getDependsOn()))
                .collect(Collectors.toList());
    }

    private void savePreferredDays(Task task, List<java.time.DayOfWeek> days) {
        if (days == null) return;
        for (var day : days) {
            TaskPreferredDay tpd = new TaskPreferredDay();
            tpd.setTask(task);
            tpd.setDayOfWeek(day);
            preferredDayRepository.save(tpd);
        }
    }

    private void saveDependencies(Task task, List<Long> dependsOnIds) {
        if (dependsOnIds == null) return;
        for (Long dependsOnId : dependsOnIds) {
            Task dependsOn = taskRepository.findById(dependsOnId)
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + dependsOnId));
            TaskDependency dependency = new TaskDependency();
            dependency.setTask(task);
            dependency.setDependsOn(dependsOn);
            taskDependencyRepository.save(dependency);
        }
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setType(task.getType());
        response.setPriority(task.getPriority());
        response.setTotalMinutes(task.getTotalMinutes());
        response.setRemainingMinutes(task.getRemainingMinutes());
        response.setSplittable(task.getSplittable());
        response.setCompleted(task.getCompleted());
        response.setCompletedDate(task.getCompletedDate());

        if (task.getType() == TaskType.ONE_TIME) {
            response.setDdl(task.getDdl());
        } else if (task.getType() == TaskType.RECURRING) {
            response.setCycleType(task.getCycleType());
            response.setCycleCount(task.getCycleCount());
            List<java.time.DayOfWeek> preferredDays = preferredDayRepository.findByTaskId(task.getId())
                    .stream()
                    .map(TaskPreferredDay::getDayOfWeek)
                    .collect(Collectors.toList());
            response.setPreferredDays(preferredDays);
        }

        List<TaskResponse> deps = taskDependencyRepository.findByTaskId(task.getId())
                .stream()
                .map(d -> {
                    TaskResponse dep = new TaskResponse();
                    dep.setId(d.getDependsOn().getId());
                    dep.setTitle(d.getDependsOn().getTitle());
                    return dep;
                })
                .collect(Collectors.toList());
        response.setDependencies(deps);

        List<TaskResponse.DoneSession> doneSessions = allocationRepository.findByTaskId(task.getId())
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getDone()))
                .sorted(java.util.Comparator.comparing(a -> a.getDayEntry().getDate()))
                .map(a -> new TaskResponse.DoneSession(
                        a.getDayEntry().getDate(),
                        a.getActualMinutes()))
                .collect(Collectors.toList());
        response.setDoneSessions(doneSessions);

        return response;
    }
}
