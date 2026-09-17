# Automatic Train Interlocking System

Academic prototype (Spring Boot / Java 21) that loads a railway layout from XML,
lets a user configure initial track occupancy, finds and reserves a safe route
between two tracks, simulates train movement, and persists state across
iterative requests. Built from the project's SRS.

**Status:** work in progress, built day by day. See commit history for progress
by day.

## Run it

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`. There is no web dashboard yet — use
the REST API directly, e.g.:

```bash
curl -X POST http://localhost:8080/api/layout --data-binary "@sample-layouts/simple-line.xml" -H "Content-Type: application/xml"
curl -X POST http://localhost:8080/api/occupancy -H "Content-Type: application/json" -d "{\"trackSectionIds\":[\"T1\"]}"
curl -X POST http://localhost:8080/api/route -H "Content-Type: application/json" -d "{\"originId\":\"T1\",\"destinationId\":\"T5\"}"
curl -X POST http://localhost:8080/api/route/release -H "Content-Type: application/json" -d "{\"trackSectionIds\":[\"T2\",\"T3\",\"T4\",\"T5\"]}"
curl http://localhost:8080/api/state
curl -X POST http://localhost:8080/api/state/clear
```

`origin`/`destination` may each be a track section id or a markerboard id. Route
finding rejects a request if any section on the only available path is already
occupied or reserved by another route, and reports which points (if any) the
route holds.

## Test it

```bash
mvn test
```

## Layout format

`sample-layouts/` holds example XML layouts, including the real interlocking
export format (`lvr_1.xml`) this project targets: track sections (some of type
`point`, with `plus`/`minus`/`stem` neighbors) and markerboards under an
`interlocking`/`network` wrapper.
