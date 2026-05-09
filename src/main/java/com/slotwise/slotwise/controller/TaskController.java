package com.slotwise.slotwise.controller;

import com.slotwise.slotwise.dto.request.StudyTaskRequest;
import com.slotwise.slotwise.dto.request.WorkoutTaskRequest;
import com.slotwise.slotwise.dto.response.TaskResponse;
import com.slotwise.slotwise.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/study")
    public TaskResponse createStudyTask(@RequestBody StudyTaskRequest request) {
        return taskService.createStudyTask(request);
    }

    @PostMapping("/workout")
    public TaskResponse createWorkoutTask(@RequestBody WorkoutTaskRequest request) {
        return taskService.createWorkoutTask(request);
    }

    @PutMapping("/study/{id}")
    public TaskResponse updateStudyTask(@PathVariable Long id, @RequestBody StudyTaskRequest request) {
        return taskService.updateStudyTask(id, request);
    }

    @PutMapping("/workout/{id}")
    public TaskResponse updateWorkoutTask(@PathVariable Long id, @RequestBody WorkoutTaskRequest request) {
        return taskService.updateWorkoutTask(id, request);
    }

    @GetMapping
    public List<TaskResponse> getAllTasks() {
        return taskService.getAllTasks();
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }

    @PostMapping("/{taskId}/dependencies/{dependsOnId}")
    public void addDependency(@PathVariable Long taskId, @PathVariable Long dependsOnId) {
        taskService.addDependency(taskId, dependsOnId);
    }

    @DeleteMapping("/{taskId}/dependencies/{dependsOnId}")
    public void removeDependency(@PathVariable Long taskId, @PathVariable Long dependsOnId) {
        taskService.removeDependency(taskId, dependsOnId);
    }

    @GetMapping("/{taskId}/dependencies")
    public List<TaskResponse> getDependencies(@PathVariable Long taskId) {
        return taskService.getDependencies(taskId);
    }
}