# Quartz Demo - Asynchronous Task Monitoring System

A Spring Boot application demonstrating the use of Quartz Scheduler for monitoring asynchronous tasks and their status updates in a database queue.

## Overview

This application provides a robust solution for monitoring asynchronous tasks using Quartz Scheduler. It integrates with an Oracle database to track task statuses and provides mechanisms to schedule, monitor, and manage task execution.

## Features

- Task scheduling with configurable timeouts
- Real-time status monitoring through database queue
- Distributed queue processing with exactly-once semantics
- Oracle database integration
- Clustered Quartz configuration for high availability

## Prerequisites

- Java 11 or higher
- Oracle Database
- Maven

## Configuration

### Database Setup

1. Create the necessary tables using the `schema.sql` file
2. Update `application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Quartz Configuration

The application uses JDBC job store for Quartz with the following configuration:
- Clustered mode enabled
- Oracle-specific delegate
- Custom table prefix (DUE_BY_)
- Connection pool settings

## Project Structure

```
src/main/java/com/example/quartz_demo/
├── model/
│   ├── Task.java
│   └── InvocationStatus.java
├── service/
│   ├── AsyncTaskMonitor.java
│   └── QueueProcessorService.java
├── job/
│   └── TaskProcessorJob.java
└── QuartzDemoApplication.java
```

## Key Components

### AsyncTaskMonitor
- Handles task scheduling
- Manages job lifecycle
- Updates task statuses in the queue

### QueueProcessorService
- Processes status updates from the queue
- Uses Oracle's SELECT FOR UPDATE SKIP LOCKED for distributed locking
- Ensures exactly-once processing across multiple instances

### TaskProcessorJob
- Implements Quartz Job interface
- Processes scheduled tasks
- Updates task status in the queue

## Queue Processing

The application uses a sophisticated SQL query to process status updates efficiently and safely:

```sql
WITH RecordGroups AS (
    SELECT 
        id, 
        invocation_id, 
        invocation_status, 
        created_at,
        ROW_NUMBER() OVER (PARTITION BY invocation_id ORDER BY created_at DESC) as is_latest,
        LISTAGG(id, ',') WITHIN GROUP (ORDER BY created_at) OVER (PARTITION BY invocation_id) as group_ids
    FROM INSIGHTS_STATUS_QUEUE
    FOR UPDATE SKIP LOCKED
)
SELECT * FROM RecordGroups
WHERE ROWNUM <= 10
ORDER BY invocation_id, created_at
```

### Query Explanation

1. **Common Table Expression (CTE)**
   - Creates a temporary result set named `RecordGroups`
   - Includes all necessary fields: `id`, `invocation_id`, `invocation_status`, `created_at`

2. **Window Functions**
   - `ROW_NUMBER()`: Assigns row numbers within each `invocation_id` group
     - Orders by `created_at` DESC (newest first)
     - `is_latest = 1` indicates the newest record in its group
   - `LISTAGG()`: Concatenates all record IDs in each group
     - Groups by `invocation_id`
     - Orders by `created_at`
     - Example: "1,2,4" for a group with IDs 1, 2, and 4

3. **Locking Mechanism**
   - `FOR UPDATE SKIP LOCKED`: Ensures thread safety
   - Locks selected rows for update
   - Skips already locked rows
   - Enables safe concurrent processing in clustered environments

4. **Result Limiting**
   - `WHERE ROWNUM <= 10`: Processes maximum 10 records per run
   - Applied after CTE but before final ordering

5. **Final Ordering**
   - `ORDER BY invocation_id, created_at`
   - Groups by `invocation_id`
   - Orders by `created_at` within each group

### Example

Given these records:
```
id | invocation_id | status    | created_at
1  | task1        | STARTED   | 10:00
2  | task1        | PROGRESS  | 10:01
3  | task2        | STARTED   | 10:02
4  | task1        | COMPLETE  | 10:03
```

The query returns:
```
id | invocation_id | status    | created_at | is_latest | group_ids
4  | task1        | COMPLETE  | 10:03     | 1         | 1,2,4
2  | task1        | PROGRESS  | 10:01     | 2         | 1,2,4
1  | task1        | STARTED   | 10:00     | 3         | 1,2,4
3  | task2        | STARTED   | 10:02     | 1         | 3
```

This structure enables:
- Identification of latest status per invocation (`is_latest = 1`)
- Batch deletion of related records (`group_ids`)
- Chronological processing
- Controlled batch size
- Thread-safe concurrent processing

## Usage

1. Start the application:
```bash
mvn spring-boot:run
```

2. Schedule a new task:
```java
Task task = new Task();
task.setTaskId("unique-id");
task.setInvocationId("invocation-id");
task.setTimeout(300); // 5 minutes
asyncTaskMonitor.scheduleTask(task);
```

3. Queue Processing:
- The application automatically processes status updates from the queue every 5 seconds
- Multiple instances can run concurrently, with each record processed exactly once
- Failed records remain in the queue for retry

## Database Schema

### INSIGHTS_STATUS_QUEUE
- id: UUID
- invocation_id: String
- invocation_status: String
- created_at: Timestamp

### DUE_BY_TRIGGERS
- JOB_NAME: String
- Other Quartz-specific columns

## Contributing

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## Author

Anjali Nair
- GitHub: [@anjalipn](https://github.com/anjalipn)
- LinkedIn: [Anjali Nair](https://www.linkedin.com/in/anjali-nair-34a28335/)

## Scheduling Mechanism

### How It Works
1. When a task is scheduled:
   - A new job is created in Quartz with a unique task ID
   - The job is configured with the invocation ID and timeout
   - A trigger is created to execute the job after the specified timeout

2. Database Tables Used:
   - `DUE_BY_TRIGGERS`: Stores Quartz trigger information
     - Contains job names, trigger states, and execution schedules
     - Used by Quartz to manage job execution
   
   - `DUE_BY_JOB_DETAILS`: Stores job configuration
     - Contains job class, data map (invocation ID)
     - Persists job metadata
   
   - `DUE_BY_FIRED_TRIGGERS`: Tracks currently executing jobs
     - Records when jobs start and complete
     - Used for job state management
   
   - `DUE_BY_SIMPLE_TRIGGERS`: Stores one-time trigger information
     - Contains the exact time when the job should execute
     - Used for timeout-based scheduling

3. Monitoring Process:
   - Every 5 seconds, the system checks `INSIGHTS_STATUS_QUEUE`
   - If a status update is found, it:
     - Retrieves the corresponding job from Quartz
     - Updates the job state based on the status
     - Cleans up completed or failed jobs

4. Job Lifecycle:
   - Created: When `scheduleTask()` is called
   - Scheduled: Stored in Quartz tables with trigger
   - Executed: When timeout is reached
   - Monitored: Status checked against queue
   - Cleaned up: When status indicates completion

### Table Relationships
```
DUE_BY_JOB_DETAILS
    ↓ (1:1)
DUE_BY_TRIGGERS
    ↓ (1:1)
DUE_BY_SIMPLE_TRIGGERS
    ↓ (1:1)
DUE_BY_FIRED_TRIGGERS
    ↓ (1:1)
INSIGHTS_STATUS_QUEUE
```
