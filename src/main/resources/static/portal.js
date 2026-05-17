const state = {
  catalog: null,
  selectedScenarioId: null,
  evidence: JSON.parse(sessionStorage.getItem("uatEvidence") || "{}")
};

document.addEventListener("DOMContentLoaded", () => {
  loadScenarioCatalog();
});

async function loadScenarioCatalog() {
  const response = await fetch("/api/v1/sandbox/scenarios");
  state.catalog = await response.json();
  document.getElementById("base-url").textContent = state.catalog.baseUrl;
}
