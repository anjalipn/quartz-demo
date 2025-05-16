package com.example.quartz_demo.service;

import com.example.quartz_demo.model.Task;
import com.example.quartz_demo.model.InvocationStatus;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

        // Check if invocationId exists in DUE_BY tables
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DUE_BY_TRIGGERS WHERE JOB_NAME = ?",
            Integer.class,
            invocationId
        );

        if (count != null && count > 0) {
            System.out.println("Hello");
        }
    }

    @Scheduled(fixedDelay = 5000) // Check every 5 seconds
    @Transactional
    public void monitorStatusQueue() {
        // Get the latest status updates from the queue
        List<Map<String, Object>> statusUpdates = jdbcTemplate.queryForList(
            "SELECT invocation_id, invocation_status FROM INSIGHTS_STATUS_QUEUE " +
            "WHERE created_at > (SELECT NVL(MAX(created_at), TO_DATE('1970-01-01', 'YYYY-MM-DD')) " +
            "FROM INSIGHTS_STATUS_QUEUE WHERE processed = 'Y')"
        );

        for (Map<String, Object> update : statusUpdates) {
            String invocationId = (String) update.get("invocation_id");
            String status = (String) update.get("invocation_status");

            // Check if this invocation ID exists in our scheduled jobs
            try {
                // Get all job keys
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.anyGroup())) {
                    JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                    String jobInvocationId = jobDetail.getJobDataMap().getString("invocationId");
                    
                    if (invocationId.equals(jobInvocationId)) {
                        // If status is not IN_PROGRESS, unschedule the job
                        if (!InvocationStatus.IN_PROGRESS.name().equals(status)) {
                            scheduler.deleteJob(jobKey);
                            System.out.println("Unscheduled job for invocation ID: " + invocationId + " due to status: " + status);
                        }
                    }
                }
            } catch (SchedulerException e) {
                System.err.println("Error processing status update for invocation ID: " + invocationId);
                e.printStackTrace();
            }
        }

        // Mark the processed records
        if (!statusUpdates.isEmpty()) {
            jdbcTemplate.update(
                "UPDATE INSIGHTS_STATUS_QUEUE SET processed = 'Y' " +
                "WHERE created_at <= (SELECT MAX(created_at) FROM INSIGHTS_STATUS_QUEUE)"
            );
        }
    }
} 