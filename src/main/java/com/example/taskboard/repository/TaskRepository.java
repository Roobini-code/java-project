package com.example.taskboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.model.Task;
import com.example.taskboard.model.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByCreatedAtDesc();

    long countByStatus(TaskStatus status);
}