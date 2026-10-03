package com.example.taskboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskBoardApplication {

    public static void main(String[] args) {
        createDataDirectory();
        SpringApplication.run(TaskBoardApplication.class, args);
    }

    private static void createDataDirectory() {
        String dataDirectory = System.getenv().getOrDefault("DATA_DIR", "./data");
        try {
            Files.createDirectories(Path.of(dataDirectory));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the database directory: " + dataDirectory, exception);
        }
    }
}