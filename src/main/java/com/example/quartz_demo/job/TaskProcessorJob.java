package com.example.quartz_demo.job;

import com.example.quartz_demo.model.InvocationStatus;
import com.example.quartz_demo.service.AsyncTaskMonitor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskProcessorJob implements Job {

    @Autowired
    private AsyncTaskMonitor asyncTaskMonitor;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String invocationId = context.getJobDetail().getJobDataMap().getString("invocationId");
        asyncTaskMonitor.updateStatus(invocationId, InvocationStatus.IN_PROGRESS);
        
        // Add your task processing logic here
        
        asyncTaskMonitor.updateStatus(invocationId, InvocationStatus.SUCCESSFUL);
    }
} 