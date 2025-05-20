package com.example.quartz_demo.service;

import com.example.quartz_demo.model.Task;
import com.example.quartz_demo.model.InvocationStatus;
import com.example.quartz_demo.job.TaskProcessorJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class AsyncTaskMonitor {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void scheduleTask(Task task) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(TaskProcessorJob.class)
                .withIdentity(task.getTaskId())
                .usingJobData("invocationId", task.getInvocationId())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(task.getTaskId() + "_trigger")
                .startAt(Date.from(Instant.now().plusSeconds(task.getTimeout())))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }

    @Transactional
    public void unscheduleTask(String taskId, String invocationId) throws SchedulerException {
        // Delete the job and its trigger
        boolean deleted = scheduler.deleteJob(new JobKey(taskId));
        
        if (deleted) {
            // Update the status to CANCELLED
            updateStatus(invocationId, InvocationStatus.CANCELLED);
        }
    }

    @Transactional
    public void updateStatus(String invocationId, InvocationStatus status) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO INSIGHTS_STATUS_QUEUE (id, invocation_id, invocation_status) VALUES (?, ?, ?)",
            id, invocationId, status.name()
        );
    }
} 