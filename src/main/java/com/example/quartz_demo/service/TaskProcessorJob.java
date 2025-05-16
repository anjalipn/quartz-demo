package com.example.quartz_demo.service;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class TaskProcessorJob implements Job {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String invocationId = context.getJobDetail().getJobDataMap().getString("invocationId");
        
        // Only monitor the status in the queue
        String status = jdbcTemplate.queryForObject(
            "SELECT invocation_status FROM INSIGHTS_STATUS_QUEUE WHERE invocation_id = ? ORDER BY created_at DESC FETCH FIRST 1 ROW ONLY",
            String.class,
            invocationId
        );
        
        if (status != null) {
            System.out.println("Current status for invocation " + invocationId + ": " + status);
        }
    }
} 