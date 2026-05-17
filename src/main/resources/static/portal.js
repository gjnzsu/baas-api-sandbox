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
        label.innerHTML = `
          <strong>${escapeHtml(shortTitle(scenario.title))}</strong>
          <small>${escapeHtml(summaryForScenario(scenario))}</small>
        `;
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
      <h4>Request setup</h4>
      <pre class="request-preview">${escapeHtml(requestPreview(step))}</pre>
      <h4>Expected outcome</h4>
      <div class="expected-card">${expectedPreview(step.expected)}</div>
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
  const headers = Object.entries(curlHeaders(step))
    .map(([name, value]) => `  -H "${name}: ${value}"`)
    .join(" \\\n");
  const body = step.body && Object.keys(step.body).length > 0
    ? ` \\\n  -d '${JSON.stringify(step.body)}'`
    : "";
  return `curl -X ${step.method} "${url}" \\\n${headers}${body}`;
}

function curlHeaders(step) {
  if (step.method === "GET") {
    return step.headers || {};
  }
  return {
    "Content-Type": "application/json",
    ...(step.headers || {})
  };
}

function renderEvidence() {
  const scenario = selectedScenario();
  const evidence = state.evidence[scenario.id];
  document.getElementById("completion-status").textContent = evidence?.complete ? "Complete" : "Not complete";
  document.getElementById("evidence-content").innerHTML = evidence ? evidencePreview(evidence) : emptyEvidence();
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

function shortTitle(title) {
  return title.replace(" initialization", "");
}

function summaryForScenario(scenario) {
  const first = scenario.steps[0];
  const expected = first.expected;
  if (expected.errorCode) {
    return `${expected.httpStatus} ${expected.errorCode}`;
  }
  if (expected.paymentStatus) {
    return `${expected.httpStatus} ${expected.paymentStatus}`;
  }
  return `${first.method} ${first.path}`;
}

function requestPreview(step) {
  const lines = [`${step.method} ${step.path}`];
  for (const [name, value] of Object.entries(step.headers || {})) {
    lines.push(`${name}: ${value}`);
  }
  if (step.body && Object.keys(step.body).length > 0) {
    lines.push("");
    lines.push(JSON.stringify(step.body));
  }
  return lines.join("\n");
}

function expectedPreview(expected) {
  const lines = [`HTTP ${expected.httpStatus}`];
  if (expected.paymentStatus) {
    lines.push(`Payment status: ${expected.paymentStatus}`);
  }
  if (expected.errorCode) {
    lines.push(`Error code: ${expected.errorCode}`);
  }
  if (expected.eventStatuses && expected.eventStatuses.length > 0) {
    lines.push(`Lifecycle events: ${expected.eventStatuses.join(" -> ")}`);
  }
  return `<p>${lines.map(escapeHtml).join("<br>")}</p>`;
}

function emptyEvidence() {
  return `
    <p>No run captured yet.</p>
    <p class="muted">Evidence is local to this browser session.</p>
  `;
}

function evidencePreview(evidence) {
  const latest = evidence.stepResults?.at(-1);
  const statusClass = evidence.expectedMatch ? "match-pass" : "match-fail";
  return `
    <dl class="evidence-summary">
      <div><dt>Timestamp</dt><dd>${escapeHtml(evidence.timestamp)}</dd></div>
      <div><dt>Scenario</dt><dd>${escapeHtml(evidence.scenarioId)}</dd></div>
      <div><dt>HTTP status</dt><dd>${escapeHtml(latest?.httpStatus ?? "External")}</dd></div>
      <div><dt>Payment ID</dt><dd>${escapeHtml(evidence.paymentId || "-")}</dd></div>
      <div><dt>Expected match</dt><dd class="${statusClass}">${escapeHtml(String(Boolean(evidence.expectedMatch)))}</dd></div>
    </dl>
    ${latest ? `<h4>Response body</h4><pre class="response-preview">${escapeHtml(JSON.stringify(latest.responseBody, null, 2))}</pre>` : ""}
    ${lifecyclePreview(latest)}
    <h4>Actual result summary</h4>
    <div class="${evidence.expectedMatch ? "result-card success" : "result-card failure"}">
      ${escapeHtml(resultSummary(evidence, latest))}
    </div>
  `;
}

function lifecyclePreview(latest) {
  const body = latest?.responseBody;
  const events = Array.isArray(body) ? body : body?.events;
  if (!events || events.length === 0) {
    return "";
  }
  return `
    <h4>Lifecycle events</h4>
    <ol class="event-list">
      ${events.map((event) => `<li>${escapeHtml(event.status)}</li>`).join("")}
    </ol>
  `;
}

function resultSummary(evidence, latest) {
  if (evidence.external) {
    return "Marked complete manually after external Postman/curl execution.";
  }
  if (!latest) {
    return "No portal-run result captured.";
  }
  if (evidence.expectedMatch) {
    return `Matched expected outcome: HTTP ${latest.httpStatus}${evidence.paymentId ? `, paymentId ${evidence.paymentId}` : ""}`;
  }
  return `Did not match expected outcome: HTTP ${latest.httpStatus}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
