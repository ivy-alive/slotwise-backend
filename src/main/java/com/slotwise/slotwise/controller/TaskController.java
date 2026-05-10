package com.slotwise.slotwise.controller;

import com.slotwise.slotwise.dto.request.TaskProgressRequest;
import com.slotwise.slotwise.dto.request.TaskRequest;
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

    @PostMapping
    public TaskResponse createTask(@RequestBody TaskRequest request) {
        return taskService.createTask(request);
    }

    @PutMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @RequestBody TaskRequest request) {
        return taskService.updateTask(id, request);
    }

    @GetMapping
    public List<TaskResponse> getAllTasks() {
        return taskService.getAllTasks();
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @PutMapping("/{id}/progress")
    public TaskResponse updateProgress(@PathVariable Long id, @RequestBody TaskProgressRequest request) {
        return taskService.updateProgress(id, request);
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
