# SlotWise Backend

A personal scheduling system for managing tasks and fitting them into daily free time, built with Spring Boot.

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring Data JPA
- MySQL 8
- Lombok
- SpringDoc OpenAPI (Swagger)

## Getting Started

### Prerequisites

- Java 17
- Docker Desktop

### 1. Configure the Database

Copy the template and fill in your credentials:

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
```

### 2. Start MySQL

```bash
docker run --name slotwise-mysql \
  -e MYSQL_ROOT_PASSWORD=your_password_here \
  -e MYSQL_DATABASE=slotwise \
  -p 3306:3306 \
  -d mysql:8
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

### 4. API Documentation

Visit [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## Requirements

### Req 1 — Task Management

Users can create, edit, and delete two types of tasks:

**One-time Tasks**
- Title, priority (Must / Normal), total duration
- Splittable: whether the task can be spread across multiple days/slots
- Deadline (optional): hard cutoff; system boosts priority when ≤ 3 days away
- Depends on: other tasks that must be completed first; blocks scheduling until met
- Status and remaining minutes can be edited from the task list

**Recurring Tasks**
- Title, priority (Must / Normal), duration per session
- Splittable: whether a single session can be split across slots
- Cycle type (Weekly / Monthly) and cycle count (N sessions per cycle)
- Preferred days: optional days of the week the task prefers to be scheduled on
- Cycle debt: sessions missed in the previous cycle are carried over and prioritised

### Req 2 — Daily Scheduling

- User selects a date on a calendar
- User inputs one or more free time slots for that day (e.g. 09:00–11:00, 20:00–22:00)
- System auto-generates a daily plan by fitting tasks into those slots:
  - **Step 1 — Recurring tasks with preferred days:** scheduled first on their designated days
    - Must (HIGH): flagged as conflict if no time available
    - Normal (LOW): silently skipped if no time available
    - Non-splittable tasks require a contiguous block; splittable tasks fill across slots
  - **Step 2 — All remaining tasks** (one-time + recurring without a preferred day today):
    - Sorted by: cycle debt → deadline within 3 days → priority
    - Recurring tasks only appear here if behind on their cycle quota
    - Splittable tasks fill available time; non-splittable tasks require a single block
    - Tasks with unmet dependencies are skipped
- Free slots can be added, edited, and deleted
- Calendar shows public holidays (via Nager.Date API) and weekends in red
- Week overview shows all 7 days with task summaries; a single Update button re-schedules all days in the week that have entries

### Req 3 — Actual Time Logging

After completing (or attempting) a task each day, the user logs progress via an inline form:

**Done:**
- Mark the allocation as Done, enter actual minutes used, add an optional memo
- Consumed minutes are incremented on the task; task is marked completed when fully consumed

**Not Done:**
- Mark as Not Done, enter minutes spent, optionally override the remaining minutes, add a memo
- Consumed minutes are updated accordingly

Logged sessions (both done and not done) appear on the Tasks page with date, minutes, and memo.

### Req 4 — Cycle Tracking & Debt

- Recurring tasks with a cycle requirement (weekly/monthly N times) are tracked
- If a previous cycle ended with fewer completions than required,
  the shortfall (cycleDebt) is carried over to the current cycle
- Cycle debt settlement happens automatically each time a schedule is generated
- Tasks behind on their cycle quota are boosted to highest scheduling priority

---

## Data Model

```mermaid
erDiagram
    tasks {
        Long id PK
        String title
        String type "ONE_TIME | RECURRING"
        String priority "HIGH | LOW"
        Integer totalMinutes
        Integer consumedMinutes
        Boolean splittable
        Boolean completed
        Date completedDate
        Date lastDoneDate
        Date ddl "ONE_TIME only"
        String cycleType "WEEKLY | MONTHLY — RECURRING only"
        Integer cycleCount "RECURRING only"
        Integer cycleDebt "RECURRING only"
    }

    task_preferred_days {
        Long id PK
        Long task_id FK
        String dayOfWeek
    }

    task_dependencies {
        Long id PK
        Long task_id FK
        Long depends_on_id FK
    }

    day_entries {
        Long id PK
        Date date
    }

    free_slots {
        Long id PK
        Long day_entry_id FK
        Time start
        Time end
    }

    daily_task_allocations {
        Long id PK
        Long day_entry_id FK
        Long task_id FK
        Integer plannedMinutes
        Integer actualMinutes
        Boolean done
        Boolean conflict
        String memo
    }

    allocation_slots {
        Long id PK
        Long daily_task_allocation_id FK
        Long free_slot_id FK
        Time start
        Time end
        Integer minutes
    }

    tasks ||--o{ task_preferred_days : "preferred on"
    tasks ||--o{ task_dependencies : "is depended on"
    tasks ||--o{ task_dependencies : "depends on"
    tasks ||--o{ daily_task_allocations : "allocated in"
    day_entries ||--o{ free_slots : "has"
    day_entries ||--o{ daily_task_allocations : "has"
    daily_task_allocations ||--o{ allocation_slots : "split into"
    free_slots ||--o{ allocation_slots : "used by"
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | /tasks | Create task |
| PUT | /tasks/{id} | Update task |
| PUT | /tasks/{id}/progress | Update task progress (status / consumed minutes) |
| GET | /tasks | Get all tasks |
| GET | /tasks/{id} | Get task by ID |
| DELETE | /tasks/{id} | Delete task |
| POST | /tasks/{id}/dependencies/{dependsOnId} | Add dependency |
| DELETE | /tasks/{id}/dependencies/{dependsOnId} | Remove dependency |
| GET | /tasks/{id}/dependencies | Get dependencies |
| POST | /day-entries | Create day entry with free slots |
| GET | /day-entries/{date} | Get day entry |
| POST | /day-entries/{date}/free-slots | Add free slot |
| PUT | /day-entries/{date}/free-slots/{id} | Update free slot |
| DELETE | /day-entries/{date}/free-slots/{id} | Delete free slot |
| POST | /day-entries/{date}/schedule | Generate / update schedule |
| GET | /day-entries/{date}/schedule | Get existing schedule |
| PUT | /day-entries/{date}/allocations/{id} | Log actual time and memo |

---

## Project Structure

```
slotwise-backend/
└── src/main/java/com/slotwise/slotwise/
    ├── controller/     # REST controllers
    ├── service/        # Business logic
    ├── repository/     # JPA repositories
    ├── model/          # Database entities
    ├── dto/
    │   ├── request/    # Incoming request bodies
    │   └── response/   # Outgoing response bodies
    ├── enums/          # TaskType, Priority, CycleType
    ├── exception/      # GlobalExceptionHandler, ResourceNotFoundException
    └── config/         # CorsConfig
slotwise-web/
└── src/
    ├── api/            # Axios API calls (tasks.js, dayEntries.js, holidays.js)
    ├── pages/          # TasksPage, SchedulePage, StatsPage
    └── components/ui/  # shadcn/ui components
```

---

## TODO

- [ ] Cycle progress view: show per-task cycle completion status in the UI
- [ ] Stats page: rebuild for new task model (recurring task completion history)
