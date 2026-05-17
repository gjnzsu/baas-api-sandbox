const state = {
  catalog: null,
  selectedScenarioId: null,
  evidence: JSON.parse(sessionStorage.getItem("uatEvidence") || "{}")
};

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("reset-progress").addEventListener("click", resetProgress);
  document.getElementById("copy-request").addEventListener("click", copySelectedScenario);
  document.getElementById("manual-complete").addEventListener("click", markManualComplete);
  document.getElementById("run-scenario").addEventListener("click", runSelectedScenario);
  loadScenarioCatalog();
});

async function loadScenarioCatalog() {
  const response = await fetch("/api/v1/sandbox/scenarios");
  state.catalog = await response.json();
  document.getElementById("base-url").textContent = state.catalog.baseUrl;
  state.selectedScenarioId = state.catalog.scenarios[0]?.id || null;
  renderScenarioList();
  renderSelectedScenario();
}

function renderScenarioList() {
  const host = document.getElementById("scenario-list");
  host.innerHTML = "";
  for (const category of state.catalog.categories) {
    const title = document.createElement("h3");
    title.className = "category-title";
    title.textContent = category;
    host.appendChild(title);

    state.catalog.scenarios
      .filter((scenario) => scenario.category === category)
      .forEach((scenario) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "scenario-button";
        button.setAttribute("aria-selected", String(scenario.id === state.selectedScenarioId));
        button.addEventListener("click", () => {
          state.selectedScenarioId = scenario.id;
          renderScenarioList();
          renderSelectedScenario();
        });

        const dot = document.createElement("span");
        dot.className = `status-dot ${state.evidence[scenario.id]?.complete ? "complete" : ""}`;
        const label = document.createElement("span");
        label.textContent = scenario.title;
        button.append(dot, label);
        host.appendChild(button);
      });
  }
  renderProgress();
}

function renderProgress() {
  const total = state.catalog.scenarios.length;
  const complete = state.catalog.scenarios.filter((scenario) => state.evidence[scenario.id]?.complete).length;
  document.getElementById("progress-summary").textContent = `${complete}/${total} complete`;
}

function selectedScenario() {
  return state.catalog.scenarios.find((scenario) => scenario.id === state.selectedScenarioId);
}

function renderSelectedScenario() {
  const scenario = selectedScenario();
  if (!scenario) {
    return;
  }

  document.getElementById("scenario-title").textContent = scenario.title;
  document.getElementById("scenario-category").textContent = scenario.category;
  document.getElementById("scenario-purpose").textContent = scenario.purpose;

  const steps = document.getElementById("scenario-steps");
  steps.innerHTML = "";
  scenario.steps.forEach((step, index) => {
    const section = document.createElement("section");
    section.className = "step";
    section.innerHTML = `
      <h3>${index + 1}. ${escapeHtml(step.title)}</h3>
      <p><strong>${escapeHtml(step.method)}</strong> ${escapeHtml(step.path)}</p>
      <h4>Headers</h4>
      <pre>${escapeHtml(JSON.stringify(step.headers, null, 2))}</pre>
      <h4>Body</h4>
      <pre>${escapeHtml(JSON.stringify(step.body || {}, null, 2))}</pre>
      <h4>Expected</h4>
      <pre>${escapeHtml(JSON.stringify(step.expected, null, 2))}</pre>
    `;
    steps.appendChild(section);
  });

  renderEvidence();
}

function copySelectedScenario() {
  const scenario = selectedScenario();
  const text = scenario.steps.map((step) => buildCurl(step)).join("\n\n");
  document.getElementById("copy-output").textContent = text;
  navigator.clipboard?.writeText(text);
}

function buildCurl(step) {
  const url = `${state.catalog.baseUrl}${step.path}`;
  const headers = Object.entries(step.headers || {})
    .map(([name, value]) => `  -H "${name}: ${value}"`)
    .join(" \\\n");
  const body = step.body && Object.keys(step.body).length > 0
    ? ` \\\n  -d '${JSON.stringify(step.body)}'`
    : "";
  return `curl -X ${step.method} "${url}" \\\n${headers}${body}`;
}

function renderEvidence() {
  const scenario = selectedScenario();
  const evidence = state.evidence[scenario.id];
  document.getElementById("completion-status").textContent = evidence?.complete ? "Complete" : "Not complete";
  document.getElementById("evidence-content").innerHTML = evidence
    ? `<pre>${escapeHtml(JSON.stringify(evidence, null, 2))}</pre>`
    : "No run captured yet. Evidence is local to this browser session.";
}

function saveEvidence() {
  sessionStorage.setItem("uatEvidence", JSON.stringify(state.evidence));
  renderScenarioList();
  renderEvidence();
}

function markManualComplete() {
  const scenario = selectedScenario();
  state.evidence[scenario.id] = {
    scenarioId: scenario.id,
    timestamp: new Date().toISOString(),
    complete: true,
    external: true,
    note: "Manually marked complete after external Postman/curl execution"
  };
  saveEvidence();
}

function resetProgress() {
  state.evidence = {};
  sessionStorage.removeItem("uatEvidence");
  renderScenarioList();
  renderEvidence();
}

async function runSelectedScenario() {
  const scenario = selectedScenario();
  const captures = {};
  const results = [];

  for (const step of scenario.steps) {
    const preparedStep = interpolateStep(step, captures);
    const response = await fetch(preparedStep.path, {
      method: preparedStep.method,
      headers: {
        "Content-Type": "application/json",
        ...(preparedStep.headers || {})
      },
      body: preparedStep.method === "GET" ? undefined : JSON.stringify(preparedStep.body || {})
    });

    const body = await readResponseBody(response);
    applyCaptures(step.capture || {}, body, captures);

    results.push({
      stepId: step.id,
      request: preparedStep,
      httpStatus: response.status,
      responseBody: body,
      expected: step.expected,
      match: matchesExpected(response.status, body, step.expected)
    });
  }

  const complete = results.every((result) => result.match);
  const paymentId = captures.paymentId || findPaymentId(results);

  state.evidence[scenario.id] = {
    scenarioId: scenario.id,
    timestamp: new Date().toISOString(),
    complete,
    expectedMatch: complete,
    paymentId,
    stepResults: results,
    localOnly: true,
  };
  saveEvidence();
}

function interpolateStep(step, captures) {
  return {
    ...step,
    path: interpolateValue(step.path, captures),
    headers: interpolateObject(step.headers || {}, captures),
    body: interpolateObject(step.body || {}, captures)
  };
}

function interpolateObject(value, captures) {
  return JSON.parse(JSON.stringify(value), (key, item) => {
    if (typeof item === "string") {
      return interpolateValue(item, captures);
    }
    return item;
  });
}

function interpolateValue(value, captures) {
  return Object.entries(captures).reduce(
    (result, [name, capturedValue]) => result.replaceAll(`{${name}}`, capturedValue),
    value
  );
}

async function readResponseBody(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function applyCaptures(captureRules, body, captures) {
  for (const [name, path] of Object.entries(captureRules)) {
    if (path === "$.paymentId" && body.paymentId) {
      captures[name] = body.paymentId;
    }
  }
}

function matchesExpected(httpStatus, body, expected) {
  if (httpStatus !== expected.httpStatus) {
    return false;
  }
  if (expected.paymentStatus && body.status !== expected.paymentStatus) {
    return false;
  }
  if (expected.errorCode && body.code !== expected.errorCode) {
    return false;
  }
  if (expected.eventStatuses && expected.eventStatuses.length > 0) {
    const statuses = Array.isArray(body)
      ? body.map((event) => event.status)
      : (body.events || []).map((event) => event.status);
    return expected.eventStatuses.every((status, index) => statuses[index] === status);
  }
  return true;
}

function findPaymentId(results) {
  for (const result of results) {
    if (result.responseBody?.paymentId) {
      return result.responseBody.paymentId;
    }
  }
  return null;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
