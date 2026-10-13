# Automatic Train Interlocking System

Academic prototype (Spring Boot / Java 21) that loads a railway layout from XML,
lets a user configure initial track occupancy, finds and reserves a safe route
between two tracks, simulates train movement, and persists state across
iterative requests. Built from the project's SRS.

**Status:** feature-complete per the SRS (FR-01–FR-18, NFR-01–NFR-06); all 62
tests pass. Built day by day — see commit history.

## Prerequisites

- Java 21+ (JDK)
- Maven 3.9+ (or use the included `mvnw` / `mvnw.cmd` wrapper if present)

No database or other external service is required — state is kept in memory
and resets whenever the app restarts.

## Run it

From the project root:

```bash
mvn spring-boot:run
```

Wait for a line like `Started InterlockingApplication in ... seconds` in the
console — that means the server is up. It listens on
`http://localhost:8080`.

### Option A: use the web dashboard

Open [http://localhost:8080](http://localhost:8080) in a browser. The
dashboard (`src/main/resources/static/`) lets you load one of the sample
layouts, set initial occupancy, find/reserve a route, simulate movement, and
inspect state, all without touching the API directly.

Suggested walkthrough:
1. **Layout** — pick a sample (e.g. `junction-layout.xml`) from the dropdown
   and click **Load sample**, or upload your own XML with **Choose file** +
   **Load layout**.
2. **Track Occupancy** — click a track section chip to mark it occupied (it
   turns red), then **Apply occupancy**.
3. **Route Request** — pick a source and destination, click
   **Find & reserve route**. The **Current State** panel at the bottom
   updates live (green = free, orange = reserved, red = occupied).
4. Click **Simulate movement** to move the train along the reserved route —
   occupancy shifts to the destination and the reservation clears.
5. **Clear state** resets occupancy/reservations but keeps the layout loaded.

### Option B: use the REST API directly

`origin`/`destination` below may each be a track section id or a markerboard
id.

```bash
# 1. Load a layout
curl -X POST http://localhost:8080/api/layout --data-binary "@sample-layouts/simple-line.xml" -H "Content-Type: application/xml"

# 2. Set initial occupancy (put a train on T1)
curl -X POST http://localhost:8080/api/occupancy -H "Content-Type: application/json" -d "{\"trackSectionIds\":[\"T1\"]}"

# 3. Find and reserve a route
curl -X POST http://localhost:8080/api/route -H "Content-Type: application/json" -d "{\"originId\":\"T1\",\"destinationId\":\"T5\"}"

# 4. Simulate the train moving along that route (use the path returned by step 3)
curl -X POST http://localhost:8080/api/route/simulate -H "Content-Type: application/json" -d "{\"trackSectionPath\":[\"T1\",\"T2\",\"T3\",\"T4\",\"T5\"]}"

# Inspect current state at any point
curl http://localhost:8080/api/state

# Release a reservation without simulating movement (e.g. cancel a route)
curl -X POST http://localhost:8080/api/route/release -H "Content-Type: application/json" -d "{\"trackSectionIds\":[\"T2\",\"T3\",\"T4\",\"T5\"]}"

# Clear occupancy/reservations but keep the loaded layout
curl -X POST http://localhost:8080/api/state/clear
```

`origin`/`destination` may each be a track section id or a markerboard id. Route
finding rejects a request if any section on the only available path is already
occupied or reserved by another route, and reports which points (if any) the
route holds.

To stop the server, press `Ctrl+C` in the terminal running `mvn spring-boot:run`.

## Test it

```bash
mvn test
```

Runs the full suite: XML parser, scenario state manager, route finder and
reservation logic, movement simulator, full-workflow integration tests (load
→ occupy → route → simulate, iterative requests, clear/reload), and a
dedicated acceptance-criteria suite covering AC-01 through AC-13 from the
SRS.

## Layout format

`sample-layouts/` holds example XML layouts, including the real interlocking
export format (`lvr_1.xml`) this project targets: track sections (some of type
`point`, with `plus`/`minus`/`stem` neighbors) and markerboards under an
`interlocking`/`network` wrapper.

## Architecture

| Package | Responsibility |
|---|---|
| `parser` | `XmlLayoutParser` — XML to `LayoutGraph`, rejects malformed input |
| `model` | Track sections, markerboards, neighbors, topology graph |
| `state` | `ScenarioStateManager` — in-memory occupancy/reservations |
| `route` | `RouteFinder` / `RouteService` — path search, safety checks, reservation |
| `simulation` | `MovementSimulator` — moves occupancy along a reserved route |
| `api` | REST controller + DTOs backing the dashboard |

## Requirements traceability

| SRS | Implemented in | Verified by |
|---|---|---|
| FR-01–FR-03, FR-18 (load/parse/graph/reject bad XML) | `XmlLayoutParser`, `LayoutGraph` | `XmlLayoutParserTest`, AcceptanceCriteriaTest |
| FR-04, FR-13, FR-14 (occupancy, persistence) | `ScenarioStateManager` | `ScenarioStateManagerTest`, `FullWorkflowIntegrationTest` |
| FR-05–FR-10 (route request, validation, reservation, rejection) | `RouteFinder`, `RouteService` | `RouteFinderTest`, AcceptanceCriteriaTest |
| FR-11, FR-12 (movement simulation, occupancy update) | `MovementSimulator` | `MovementSimulatorTest` |
| FR-15–FR-17 (iterative requests, clear, reload) | `ScenarioStateManager`, API | `FullWorkflowIntegrationTest` |
| NFR-01, NFR-06 (safety before reserve/move) | `RouteService` | AcceptanceCriteriaTest |
| NFR-02 (accept/reject reasons) | `RouteResponse` messages | `DashboardApiControllerTest` |
| NFR-03 (usability) | `static/` dashboard | manual walkthrough above |
| NFR-04, NFR-05 (testability, separation) | package layout above | `mvn test` |
