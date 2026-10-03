package com.example.taskboard.web;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.taskboard.model.TaskStatus;
import com.example.taskboard.service.TaskService;

@Controller
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("taskForm", new TaskForm());
        addDashboardAttributes(model);
        return "tasks";
    }

    @PostMapping("/tasks")
    public String createTask(@Valid @ModelAttribute("taskForm") TaskForm taskForm,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addDashboardAttributes(model);
            return "tasks";
        }

        taskService.create(taskForm.getTitle(), taskForm.getDescription());
        redirectAttributes.addFlashAttribute("notice", "Task added.");
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam TaskStatus status) {
        taskService.updateStatus(id, status);
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/delete")
    public String deleteTask(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        taskService.delete(id);
        redirectAttributes.addFlashAttribute("notice", "Task deleted.");
        return "redirect:/";
    }

    private void addDashboardAttributes(Model model) {
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("todoCount", taskService.countByStatus(TaskStatus.TODO));
        model.addAttribute("inProgressCount", taskService.countByStatus(TaskStatus.IN_PROGRESS));
        model.addAttribute("doneCount", taskService.countByStatus(TaskStatus.DONE));
    }
}