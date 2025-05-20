package com.example.quartz_demo.service;

import com.example.quartz_demo.model.InvocationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class QueueProcessorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processQueue() {
        // Get all records with their group information
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "WITH RecordGroups AS (" +
            "    SELECT " +
            "        id, " +
            "        invocation_id, " +
            "        invocation_status, " +
            "        created_at, " +
            "        ROW_NUMBER() OVER (PARTITION BY invocation_id ORDER BY created_at DESC) as is_latest, " +
            "        LISTAGG(id, ',') WITHIN GROUP (ORDER BY created_at) OVER (PARTITION BY invocation_id) as group_ids " +
            "    FROM INSIGHTS_STATUS_QUEUE " +
            "    FOR UPDATE SKIP LOCKED" +
            ") " +
            "SELECT * FROM RecordGroups " +
            "ORDER BY invocation_id, created_at"
        );

        String currentInvocationId = null;
        List<String> groupIds = null;

        for (Map<String, Object> record : records) {
            String invocationId = (String) record.get("invocation_id");
            
            // If this is a new group, process the previous group
            if (currentInvocationId != null && !currentInvocationId.equals(invocationId)) {
                processGroup(currentInvocationId, groupIds);
            }
            
            // If this is the latest record in the group, store its information
            if (record.get("is_latest").equals(1L)) {
                currentInvocationId = invocationId;
                groupIds = Arrays.asList(((String) record.get("group_ids")).split(","));
                
                try {
                    String statusStr = (String) record.get("invocation_status");
                    processStatusUpdate(invocationId, InvocationStatus.valueOf(statusStr));
                } catch (Exception e) {
                    System.err.println("Error processing status for invocation ID " + invocationId + ": " + e.getMessage());
                }
            }
        }
        
        // Process the last group
        if (currentInvocationId != null) {
            processGroup(currentInvocationId, groupIds);
        }
    }

    private void processGroup(String invocationId, List<String> groupIds) {
        try {
            // Delete all records in the group by their IDs
            jdbcTemplate.batchUpdate(
                "DELETE FROM INSIGHTS_STATUS_QUEUE WHERE id = ?",
                groupIds,
                1,
                (ps, id) -> ps.setString(1, id)
            );
        } catch (Exception e) {
            System.err.println("Error deleting records for invocation ID " + invocationId + ": " + e.getMessage());
        }
    }

    private void processStatusUpdate(String invocationId, InvocationStatus status) {
        // Add your status processing logic here
        System.out.println("Processing status update for invocation ID: " + invocationId + ", Status: " + status);
    }
} 