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
- Get an aggregated availability view for a time frame: merged `free` and `busy` intervals clipped to the window.
- Get a slot count summary for a time frame (totals by status: `free`, `booked`, `blocked`).
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
- List meetings a user organises or attends, with pagination.
- Update meeting details and participants.
- Cancel a meeting.

When a meeting is created, the application locks the slot, validates its current state, validates the participants, creates the meeting, and changes the slot to `BOOKED` in the same transaction.

When a meeting is cancelled, its slot becomes `FREE` again.

### Effective availability

`GET /api/v1/users/{userId}/slots/availability` returns an effective view for the requested
window:

- `free` contains the user's published `FREE` slots, merged and clipped to the window;
- `busy` contains the user's `BOOKED` and `BLOCKED` slots plus meetings where the user is an invited participant;
- invited meeting intervals are subtracted from `free`, but do not mutate the participant's own slots.

The summary endpoint reports persisted slot counts by status. It intentionally does not count
participant invitations because those are meetings, not participant-owned slots.

## Technology stack

- Java 25
- Spring Boot
- PostgreSQL
- Liquibase
- Testcontainers

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

## API endpoints

All business endpoints use the `/api/v1` prefix.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/users` | Create a user and personal calendar. |
| `GET` | `/users`, `/users/{userId}`, `/users/email?email=...` | List or retrieve users. |
| `PUT` | `/users/{userId}` | Update user details and timezone. |
| `POST` | `/users/{userId}/slots` | Publish a free slot. |
| `GET` | `/slots/{slotId}` | Retrieve a slot. |
| `GET` | `/users/{userId}/slots?from=...&to=...&status=...` | Query overlapping slots, optionally by status. |
| `GET` | `/users/{userId}/slots/availability?from=...&to=...` | Query merged effective free/busy intervals. |
| `GET` | `/users/{userId}/slots/summary?from=...&to=...` | Count persisted slots by status. |
| `PUT` | `/slots/{slotId}` | Change an unbooked slot's interval. |
| `PATCH` | `/slots/{slotId}/status` | Block or release an unbooked slot. |
| `DELETE` | `/slots/{slotId}` | Delete an unbooked slot. |
| `POST` | `/slots/{slotId}/meetings` | Convert a free slot into a meeting. |
| `GET` | `/meetings/{meetingId}` | Retrieve a meeting. |
| `GET` | `/users/{userId}/meetings` | List meetings organised by or inviting the user. |
| `PUT` | `/meetings/{meetingId}` | Update meeting details and participants. |
| `DELETE` | `/meetings/{meetingId}` | Cancel a meeting and release its slot. |

Timestamps use ISO-8601 with an offset, for example `2026-08-15T09:00:00Z`. Pagination uses
Spring's `page`, `size`, and `sort` query parameters. See the
[complete curl flow](docs/TESTING_COMPLETE_FLOW.md) or the Swagger UI for request and response
examples.

## Concurrency strategy

### Creating a slot

The calendar row is locked with `PESSIMISTIC_WRITE` before checking for overlaps. This serialises all interval changes within one calendar, eliminating the TOCTOU window where two concurrent requests could both pass the overlap check. Different calendars are unaffected.

### Moving a slot

The calendar row is locked first, then the slot row. Acquiring in this fixed order (calendar → slot) ensures consistency with the create path and avoids deadlocks against any future operation that acquires the same locks.

### Scheduling a meeting

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

To run the containers in the background and wait until both are healthy:

```bash
docker compose up --build -d --wait
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

The test suite includes:

- unit tests for the service layer with Mockito;
- HTTP contract tests for controllers with MockMvc;
- Spring Boot integration tests against PostgreSQL with Testcontainers, covering Liquibase/Hibernate startup, overlap rules, participant meeting queries, effective availability, and concurrent slot creation.

Run the complete suite with Docker available:

```bash
./mvnw verify
```

With Rancher Desktop on macOS, expose its Docker socket to Testcontainers. The host override is
needed by the VZ rootless runtime:

```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
export TESTCONTAINERS_HOST_OVERRIDE="$(rdctl shell ip a show vznat | awk '/inet / {sub("/.*", ""); print $2}')"
./mvnw verify
```

The GitHub Actions workflow runs the suite and then builds and health-checks the complete Docker
Compose environment.

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
- Invited meetings appear in the participant's meeting list and effective busy intervals.
- Cancelling a meeting releases its slot.
- Participant-owned slots retain their persisted status when invitations are added or removed.

## Current scope

This version intentionally excludes:

- authentication and authorisation;
- recurring meetings;
- invitation acceptance or rejection;
- email notifications;
- rejection of invitations that conflict with another participant meeting;
- external calendar integrations;
- video-conference links;
- reminders.
