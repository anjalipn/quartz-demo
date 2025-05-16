# Quartz Demo - Asynchronous Task Monitoring System

A Spring Boot application demonstrating the use of Quartz Scheduler for monitoring asynchronous tasks and their status updates in a database queue.

## Overview

This application provides a robust solution for monitoring asynchronous tasks using Quartz Scheduler. It integrates with an Oracle database to track task statuses and provides mechanisms to schedule, monitor, and manage task execution.

## Features

- Task scheduling with configurable timeouts
- Real-time status monitoring through database queue
- Automatic job cleanup based on status updates
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
│   └── TaskProcessorJob.java
└── QuartzDemoApplication.java
```

## Key Components

### AsyncTaskMonitor
- Handles task scheduling and monitoring
- Manages job lifecycle
- Updates task statuses

### TaskProcessorJob
- Implements Quartz Job interface
- Processes scheduled tasks
- Monitors task status in the queue

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

3. Monitor task status:
The application automatically monitors the status queue every 5 seconds.

## Database Schema

### INSIGHTS_STATUS_QUEUE
- id: UUID
- invocation_id: String
- invocation_status: String
- created_at: Timestamp
- processed: Char(1)

### DUE_BY_TRIGGERS
- JOB_NAME: String
- Other Quartz-specific columns

## Contributing

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Author

Anjali Nair
- GitHub: [@anjalipn](https://github.com/anjalipn)
- LinkedIn: [Anjali Nair](https://www.linkedin.com/in/anjali-nair-34a28335/)