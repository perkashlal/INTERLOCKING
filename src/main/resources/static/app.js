const layoutStatusEl = document.getElementById("layout-status");
const occupancyGridEl = document.getElementById("occupancy-grid");
const stateGridEl = document.getElementById("state-grid");
const originSelectEl = document.getElementById("origin-select");
const destinationSelectEl = document.getElementById("destination-select");
const routeResultEl = document.getElementById("route-result");
const simulateBtn = document.getElementById("simulate-btn");
const releaseBtn = document.getElementById("release-btn");

let currentState = { layoutLoaded: false, allTrackSectionIds: [], occupiedTrackIds: [], reservedTrackIds: [] };
let selectedOccupancy = new Set();
let lastRoute = null;

async function apiRequest(path, options) {
  const response = await fetch(path, options);
  let data = null;
  try {
    data = await response.json();
  } catch (err) {
    data = null;
  }
  if (!response.ok) {
    const message = (data && data.message) ? data.message : `Request failed with status ${response.status}`;
    throw new Error(message);
  }
  return data;
}

function renderLayoutStatus() {
  if (!currentState.layoutLoaded) {
    layoutStatusEl.textContent = "No layout loaded.";
    return;
  }
  layoutStatusEl.textContent = `Layout loaded: ${currentState.allTrackSectionIds.length} track section(s).`;
}

function renderOccupancyGrid() {
  occupancyGridEl.innerHTML = "";
  currentState.allTrackSectionIds.forEach((id) => {
    const chip = document.createElement("div");
    chip.className = "track-chip" + (selectedOccupancy.has(id) ? " selected" : "");
    chip.textContent = id;
    chip.title = "Click to toggle occupancy for " + id;
    chip.addEventListener("click", () => {
      if (selectedOccupancy.has(id)) {
        selectedOccupancy.delete(id);
      } else {
        selectedOccupancy.add(id);
      }
      renderOccupancyGrid();
    });
    occupancyGridEl.appendChild(chip);
  });
}

function renderRouteSelects() {
  const fillSelect = (select) => {
    const previousValue = select.value;
    select.innerHTML = "";
    currentState.allTrackSectionIds.forEach((id) => {
      const option = document.createElement("option");
      option.value = id;
      option.textContent = id;
      select.appendChild(option);
    });
    if (currentState.allTrackSectionIds.includes(previousValue)) {
      select.value = previousValue;
    }
  };
  fillSelect(originSelectEl);
  fillSelect(destinationSelectEl);
}

function statusOf(id) {
  if (currentState.occupiedTrackIds.includes(id)) return "occupied";
  if (currentState.reservedTrackIds.includes(id)) return "reserved";
  return "free";
}

function renderStateGrid() {
  stateGridEl.innerHTML = "";
  currentState.allTrackSectionIds.forEach((id) => {
    const chip = document.createElement("div");
    const status = statusOf(id);
    chip.className = "state-chip " + status;
    chip.textContent = `${id} (${status})`;
    stateGridEl.appendChild(chip);
  });
}

function renderAll() {
  renderLayoutStatus();
  renderOccupancyGrid();
  renderRouteSelects();
  renderStateGrid();
}

async function refreshState() {
  currentState = await apiRequest("/api/state");
  selectedOccupancy = new Set(currentState.occupiedTrackIds);
  renderAll();
}

function showRouteResult(kind, text) {
  routeResultEl.className = "result-box " + kind;
  routeResultEl.textContent = text;
}

function setRouteButtonsEnabled(enabled) {
  simulateBtn.disabled = !enabled;
  releaseBtn.disabled = !enabled;
}

document.getElementById("load-layout-btn").addEventListener("click", async () => {
  const fileInput = document.getElementById("layout-file");
  const file = fileInput.files[0];
  if (!file) {
    showRouteResult("error", "Choose an XML file first.");
    return;
  }
  try {
    const xmlText = await file.text();
    const summary = await apiRequest("/api/layout", {
      method: "POST",
      headers: { "Content-Type": "application/xml" },
      body: xmlText,
    });
    lastRoute = null;
    setRouteButtonsEnabled(false);
    showRouteResult(
      "success",
      `Layout loaded: ${summary.trackSectionCount} track section(s), ${summary.pointCount} point(s), ${summary.markerboardCount} markerboard(s).`
    );
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to load layout: " + err.message);
  }
});

document.getElementById("load-sample-btn").addEventListener("click", async () => {
  const sampleSelect = document.getElementById("sample-select");
  const fileName = sampleSelect.value;
  if (!fileName) {
    showRouteResult("error", "Choose a sample layout first.");
    return;
  }
  try {
    const xmlResponse = await fetch("samples/" + fileName);
    const xmlText = await xmlResponse.text();
    const summary = await apiRequest("/api/layout", {
      method: "POST",
      headers: { "Content-Type": "application/xml" },
      body: xmlText,
    });
    lastRoute = null;
    setRouteButtonsEnabled(false);
    showRouteResult(
      "success",
      `Sample layout '${fileName}' loaded: ${summary.trackSectionCount} track section(s), ${summary.pointCount} point(s), ${summary.markerboardCount} markerboard(s).`
    );
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to load sample layout: " + err.message);
  }
});

document.getElementById("apply-occupancy-btn").addEventListener("click", async () => {
  try {
    await apiRequest("/api/occupancy", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ trackSectionIds: Array.from(selectedOccupancy) }),
    });
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to set occupancy: " + err.message);
  }
});

document.getElementById("find-route-btn").addEventListener("click", async () => {
  const originId = originSelectEl.value;
  const destinationId = destinationSelectEl.value;
  if (!originId || !destinationId) {
    showRouteResult("error", "Choose both a source and a destination.");
    return;
  }
  try {
    const result = await apiRequest("/api/route", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ originId, destinationId }),
    });
    lastRoute = result;
    setRouteButtonsEnabled(true);
    const pointsText = result.pointsUsed.length > 0 ? result.pointsUsed.join(", ") : "none";
    showRouteResult(
      "success",
      `Route found: ${result.trackSectionPath.join(" -> ")}\nReserved: ${result.reservedTrackIds.join(", ")}\nPoints used: ${pointsText}`
    );
    await refreshState();
  } catch (err) {
    lastRoute = null;
    setRouteButtonsEnabled(false);
    showRouteResult("error", "No route found: " + err.message);
  }
});

simulateBtn.addEventListener("click", async () => {
  if (!lastRoute) return;
  try {
    await apiRequest("/api/route/simulate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ trackSectionPath: lastRoute.trackSectionPath }),
    });
    const destination = lastRoute.trackSectionPath[lastRoute.trackSectionPath.length - 1];
    showRouteResult("success", "Movement simulated. Train is now at " + destination);
    lastRoute = null;
    setRouteButtonsEnabled(false);
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to simulate movement: " + err.message);
  }
});

releaseBtn.addEventListener("click", async () => {
  if (!lastRoute) return;
  try {
    await apiRequest("/api/route/release", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ trackSectionIds: lastRoute.reservedTrackIds }),
    });
    showRouteResult("success", "Reservation released.");
    lastRoute = null;
    setRouteButtonsEnabled(false);
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to release reservation: " + err.message);
  }
});

document.getElementById("refresh-state-btn").addEventListener("click", refreshState);

document.getElementById("clear-state-btn").addEventListener("click", async () => {
  try {
    await apiRequest("/api/state/clear", { method: "POST" });
    lastRoute = null;
    setRouteButtonsEnabled(false);
    showRouteResult("success", "State cleared. Layout kept.");
    await refreshState();
  } catch (err) {
    showRouteResult("error", "Failed to clear state: " + err.message);
  }
});

refreshState().catch((err) => showRouteResult("error", "Failed to load state: " + err.message));
