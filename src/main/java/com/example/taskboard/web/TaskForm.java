package com.example.taskboard.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TaskForm {

    @NotBlank(message = "Enter a task title.")
    @Size(max = 120, message = "Use 120 characters or fewer.")
    private String title;

    @Size(max = 1000, message = "Use 1,000 characters or fewer.")
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}