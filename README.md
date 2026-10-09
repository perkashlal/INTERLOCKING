# Automatic Train Interlocking System

Academic prototype (Spring Boot / Java 21) that loads a railway layout from XML,
lets a user configure initial track occupancy, finds and reserves a safe route
between two tracks, simulates train movement, and persists state across
iterative requests. Built from the project's SRS.

**Status:** feature-complete against the SRS (FR-01–FR-18, NFR-01–NFR-06,
SPR-01–SPR-06, AC-01–AC-13). Built day by day; see commit history.

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

## SRS traceability

| SRS area | Where implemented | Verified by |
|---|---|---|
| FR-01–03, FR-18 (load/parse/graph/reject bad XML) | `parser/XmlLayoutParser`, layout model | `XmlLayoutParserTest`, AC-01/02/13 |
| FR-04, FR-13–17, SPR-01–06 (occupancy, persistence, clear, reload) | `state/ScenarioStateManager` | `ScenarioStateManagerTest`, AC-03/09–12 |
| FR-05–10, NFR-01/02/06 (route find, reserve, reject with reason) | `route/RouteFinder`, route service | `RouteFinderTest`, `RouteServiceTest`, AC-04–07 |
| FR-11–12 (movement simulation) | `simulation/MovementSimulator` | `MovementSimulatorTest`, AC-08 |
| NFR-03 (usability) | `static/` dashboard | `FullWorkflowIntegrationTest` |

Full suite: 62 tests, all passing (`mvn test`).
