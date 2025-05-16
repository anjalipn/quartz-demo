package com.example.quartz_demo.model;

import lombok.Data;

@Data
public class Task {
    private String taskId;
    private String invocationId;
    private int timeout; // in seconds
} 