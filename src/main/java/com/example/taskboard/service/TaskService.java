package com.example.taskboard.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.taskboard.model.Task;
import com.example.taskboard.model.TaskStatus;
import com.example.taskboard.repository.TaskRepository;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long countByStatus(TaskStatus status) {
        return taskRepository.countByStatus(status);
    }

    @Transactional
    public Task create(String title, String description) {
        String cleanDescription = description == null ? "" : description.trim();
        return taskRepository.save(new Task(title.trim(), cleanDescription));
    }

    @Transactional
    public void updateStatus(Long id, TaskStatus status) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        task.setStatus(status);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.deleteById(id);
    }
}