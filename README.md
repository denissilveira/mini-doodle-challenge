# Mini Doodle

Mini Doodle is a REST API for managing users, calendars, availability slots, and meetings.

A user publishes available time slots in their calendar. A meeting can then be scheduled in a free slot with one or more participants. The owner of the slot is the meeting organiser.

## Main features

### Users

- Create a user.
- Retrieve a user by ID or email.
- List users with pagination.
- Update a user.
- Create the user's calendar automatically.

### Slots

- Create an available slot.
- Retrieve a slot by ID.
- List a user's slots within a period.
- Filter slots by status.
- Update a slot's time range.
- Block or unblock a slot.
- Delete an unbooked slot.

Supported statuses:

| Status | Meaning |
|---|---|
| `FREE` | Available for scheduling. |
| `BLOCKED` | Unavailable because the owner blocked it. |
| `BOOKED` | Associated with a meeting. |

Clients cannot set a slot directly to `BOOKED`. That transition belongs exclusively to the meeting flow.

### Meetings

- Schedule a meeting in a free slot.
- Retrieve a meeting by ID.
- List a user's meetings with pagination.
- Update meeting details and participants.
- Cancel a meeting.

When a meeting is created, the application locks the slot, validates its current state, validates the participants, creates the meeting, and changes the slot to `BOOKED` in the same transaction.

When a meeting is cancelled, its slot becomes `FREE` again.

## Technology stack

- Java 25
- Spring Boot
- PostgreSQL
- Liquibase

## Domain model

```text
User 1 ─── 1 Calendar
Calendar 1 ─── N Slot
Slot 1 ─── 0..1 Meeting
Meeting 1 ─── N MeetingParticipant
User 1 ─── N MeetingParticipant
```

The organiser is derived from:

```text
Meeting → Slot → Calendar → User
```

The meeting participant relation is modelled explicitly through `MeetingParticipant`, which allows the association to receive additional fields later, such as invitation status or response timestamps.

## Concurrency strategy

### Creating or moving slots

The calendar row is locked with `PESSIMISTIC_WRITE` before checking overlaps. This serialises interval changes within one calendar while allowing different calendars to be modified concurrently.

### Scheduling meetings

The slot row is locked with `PESSIMISTIC_WRITE` before checking whether it is `FREE`. This prevents two concurrent requests from booking the same slot.

Locks remain active until the transaction commits or rolls back.

## Running the application

The project supports two local execution modes.

### Option 1: Run the complete environment with Docker Compose

Docker Compose builds the Spring Boot application image and starts both the application and PostgreSQL.

No local Maven build is required because the `Dockerfile` executes the Maven build inside its builder stage.

```bash
docker compose up --build
```

To run the containers in the background:

```bash
docker compose up --build -d
```

The services will be available at:

```text
Application: http://localhost:8080
PostgreSQL:  localhost:5432
```

Check the application health:

```bash
curl http://localhost:8080/actuator/health
```

View the application logs:

```bash
docker compose logs -f app
```

View the PostgreSQL logs:

```bash
docker compose logs -f postgres
```

Check the container status:

```bash
docker compose ps
```

Stop the environment:

```bash
docker compose down
```

Stop the environment and remove the PostgreSQL data:

```bash
docker compose down -v
```

> The `-v` option permanently removes the local PostgreSQL volume and all stored data.

After changing the application code, rebuild the image:

```bash
docker compose up --build
```

### Option 2: Run PostgreSQL with Docker and the application locally

Use this mode when developing through IntelliJ IDEA or running Spring Boot directly with Maven.

Start only PostgreSQL:

```bash
docker compose up -d postgres
```

Run the application using Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Or using a local Maven installation:

```bash
mvn spring-boot:run
```

The application will be available at:

```text
http://localhost:8080
```

Stop PostgreSQL:

```bash
docker compose stop postgres
```

### Build without starting the application

Run the complete Maven build:

```bash
./mvnw clean install
```

Or using a local Maven installation:

```bash
mvn clean install
```

Build only the application Docker image:

```bash
docker compose build app
```

Start previously built containers without rebuilding:

```bash
docker compose up
```

### Rebuild the complete environment from scratch

Remove the containers and PostgreSQL volume:

```bash
docker compose down -v
```

Build and start the complete environment again:

```bash
docker compose up --build
```

## Health check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

## API documentation (Swagger UI)

The application exposes an interactive OpenAPI 3 UI at:

```text
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI spec is available at:

```text
http://localhost:8080/v3/api-docs
```

## Metrics

The application exports Prometheus metrics via Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/prometheus
```

The following actuator endpoints are enabled: `health`, `info`, `metrics`, `prometheus`.

## Testing strategy

The test suite covers the service and controller layers with unit tests using Mockito and MockMvc.

Repository-level integration tests — validating the JPQL overlap queries, pagination behaviour, and pessimistic-lock semantics against a real database — would require [Testcontainers](https://testcontainers.com/) with a PostgreSQL container and `@DataJpaTest`. This layer is a known gap and would be the natural next step to increase confidence in the persistence layer.

## Business rules

- Each user owns exactly one calendar.
- A slot must end after it starts.
- Slots in the same calendar cannot overlap.
- Adjacent slots are allowed.
- New slots start as `FREE`.
- A `BOOKED` slot cannot be edited or deleted directly.
- Only a `FREE` slot can receive a meeting.
- The slot owner is the meeting organiser.
- The organiser cannot also be an invited participant.
- Every participant must reference an existing user.
- Cancelling a meeting releases its slot.
- Participant calendars are not automatically blocked in this version.

## Current scope

This version intentionally excludes:

- authentication and authorisation;
- recurring meetings;
- invitation acceptance or rejection;
- email notifications;
- automatic participant calendar blocking;
- external calendar integrations;
- video-conference links;
- reminders.
