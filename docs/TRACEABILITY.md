# Requirements Traceability

Maps SRS requirements to implementation and tests.

| Requirement | Implementation | Tests |
|---|---|---|
| FR-01, FR-02, FR-18 (load / reject XML) | `parser/XmlLayoutParser`, `exception/GlobalExceptionHandler` | `XmlLayoutParserTest`, AC-01, AC-13 |
| FR-03 (topology graph) | `model/LayoutGraph` | `XmlLayoutParserTest`, AC-02 |
| FR-04, FR-13, FR-14 (occupancy, persistence) | `state/ScenarioStateManager` | `ScenarioStateManagerTest`, AC-03 |
| FR-05 – FR-10 (route request, find, reserve, reject) | `route/RouteFinder`, `route/RouteService` | `RouteFinderTest`, `RouteServiceTest`, AC-04 – AC-08 |
| FR-11, FR-12 (movement simulation) | `simulation/MovementSimulator` | `MovementSimulatorTest`, AC-09, AC-10 |
| FR-15 (iterative requests) | `api/DashboardApiController` | `FullWorkflowIntegrationTest`, AC-12 |
| FR-16, FR-17 (clear / reload) | `ScenarioStateManager`, `/api/state/clear`, `/api/layout` | `FullWorkflowIntegrationTest`, AC-11 |
| NFR-01, NFR-06 (safety before reserve/move) | checks in `RouteService` / `MovementSimulator` | `RouteServiceTest`, `MovementSimulatorTest` |
| NFR-02 (traceability) | reasons returned in route responses | `DashboardApiControllerTest` |
| NFR-03 (usability) | `src/main/resources/static/` dashboard | manual walkthrough (README) |
| NFR-04, NFR-05 | separate packages; `mvn test` | whole suite |
