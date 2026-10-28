# Requirements Traceability

Maps SRS requirements to the components that implement them and the tests that verify them.

| SRS | Component | Verified by |
|-----|-----------|-------------|
| FR-01, FR-02, FR-18 (load / parse / reject malformed XML) | `parser/XmlLayoutParser`, `GlobalExceptionHandler` | `XmlLayoutParserTest`, AC-01, AC-13 |
| FR-03 (topology graph) | `model/LayoutGraph` | AC-02 |
| FR-04 (initial occupancy) | `state/ScenarioStateManager` | `ScenarioStateManagerTest`, AC-03 |
| FR-05 – FR-07, FR-10 (route request, accept/reject) | `route/RouteFinder`, `RouteService` | `RouteFinderTest`, `RouteServiceTest`, AC-04 – AC-06 |
| FR-08, FR-09 (reserve sections and points) | `route/RouteService` | AC-07 |
| FR-11, FR-12 (movement simulation) | `simulation/MovementSimulator` | `MovementSimulatorTest`, AC-08 |
| FR-13 – FR-15 (state persists, iterative requests) | `state/ScenarioStateManager` | `FullWorkflowIntegrationTest`, AC-09, AC-10 |
| FR-16, FR-17 (clear state, reload layout) | `DashboardApiController` | AC-11, AC-12 |

| NFR | How it is met |
|-----|---------------|
| NFR-01, NFR-06 (correctness, safety first) | Route finding checks occupancy/reservation before any reservation or movement |
| NFR-02 (traceability) | Route responses carry accept/reject reasons and held points |
| NFR-03 (usability) | Web dashboard in `src/main/resources/static/` |
| NFR-04 (testability) | 62 automated tests (`mvn test`) |
| NFR-05 (maintainability) | Separate `parser`, `route`, `state`, `simulation`, `api` packages |
