/*
 * Asynchronous client for the hardcoded services.
 *
 * The page is never reloaded: every action prevents the default form submission,
 * builds the service URL from the user input, sends the request with fetch(),
 * and updates only the result or the error area when the answer arrives.
 *
 * Three outcomes are handled separately:
 *   - a network failure   (fetch rejects: the server is unreachable)
 *   - an HTTP error       (fetch resolves with response.ok === false)
 *   - a successful answer (status checked first, only then the body is parsed)
 */

const ui = {};
let pending = 0;

/** An error already carrying a message that is safe to show to the user. */
class ServiceError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ServiceError";
    this.status = status;
  }
}

document.addEventListener("DOMContentLoaded", () => {
  ui.status = document.getElementById("status");
  ui.result = document.getElementById("result");
  ui.error = document.getElementById("error");
  ui.buttons = Array.from(document.querySelectorAll("button"));

  document.getElementById("script-status").textContent =
    "app.js was fetched in its own HTTP request and is running in the browser.";

  document.getElementById("greeting-form").addEventListener("submit", (event) => {
    // Without this the browser would navigate and the page would reload.
    event.preventDefault();
    askForGreeting();
  });

  document.getElementById("square-form").addEventListener("submit", (event) => {
    event.preventDefault();
    askForSquare();
  });

  document.getElementById("time-button").addEventListener("click", askForTime);
  document.getElementById("slow-button").addEventListener("click", askForSlowResponse);
});

/* ------------------------------------------------------------------ actions */

async function askForGreeting() {
  const name = document.getElementById("name").value.trim();
  if (name === "") {
    showError("Type a name before asking for a greeting.");
    return;
  }
  await run("Requesting a greeting", serviceUrl("/app/hello", { name }), (data) =>
    `${data.greeting}\n\nThe server echoed the name: ${data.name}`
  );
}

async function askForSquare() {
  const value = document.getElementById("value").value.trim();
  if (value === "") {
    showError("Type a number before asking for its square.");
    return;
  }
  // Anything else is validated by the server, whose 400 becomes a friendly message.
  await run("Computing the square", serviceUrl("/app/square", { value }), (data) =>
    `${data.value} squared is ${data.square}`
  );
}

async function askForTime() {
  await run("Reading the server clock", serviceUrl("/app/time"), (data) => {
    const browser = new Date().toISOString();
    document.getElementById("clock-note").textContent = "Browser clock: " + browser;
    return `Server time: ${data.iso}\nServer time zone: ${data.zone}\nBrowser time: ${browser}`;
  });
}

async function askForSlowResponse() {
  const millis = document.getElementById("millis").value.trim();
  if (millis === "") {
    showError("Type how many milliseconds the server should sleep.");
    return;
  }
  await run("Running a slow request (the server is busy)",
    serviceUrl("/app/slow", { millis }),
    (data) => `The server slept ${data.elapsedMillis} ms.\n${data.note}`);
}

/* ---------------------------------------------------------------- machinery */

/** Builds an absolute service URL, letting the browser encode the parameters. */
function serviceUrl(path, params = {}) {
  const url = new URL(path, window.location.origin);
  for (const [name, value] of Object.entries(params)) {
    url.searchParams.set(name, value);
  }
  return url;
}

/**
 * Runs one service call: shows the loading state, keeps the page interactive
 * while the answer is pending, and renders either the result or the error.
 */
async function run(label, url, render) {
  clearAreas();
  setLoading(true, label);
  const startedAt = performance.now();
  try {
    const data = await getJson(url);
    const elapsed = Math.round(performance.now() - startedAt);
    showResult(`${render(data)}\n\n(${url.pathname}${url.search} answered in ${elapsed} ms)`);
  } catch (failure) {
    if (failure instanceof ServiceError) {
      showError(failure.message);
    } else {
      // Nothing implementation-specific reaches the page.
      showError("Unexpected problem in the browser client. Try again.");
      console.error(failure);
    }
  } finally {
    setLoading(false, label);
  }
}

/** Sends the request, checks the status, and only then parses the body. */
async function getJson(url) {
  let response;
  try {
    response = await fetch(url, { headers: { Accept: "application/json" } });
  } catch (networkFailure) {
    // fetch only rejects when the request never got an HTTP answer.
    throw new ServiceError(
      "The server could not be reached. Check that it is still running and try again."
    );
  }

  if (!response.ok) {
    throw new ServiceError(await errorMessageOf(response), response.status);
  }

  try {
    return await response.json();
  } catch (parseFailure) {
    throw new ServiceError("The server answered something that is not valid JSON.");
  }
}

/** Prefers the message the service sent, falls back to the status line. */
async function errorMessageOf(response) {
  const fallback = `The server rejected the request (HTTP ${response.status} ${response.statusText}).`;
  try {
    const payload = await response.json();
    if (payload && typeof payload.message === "string") {
      return `${payload.message} (HTTP ${response.status})`;
    }
  } catch (ignored) {
    // The error body was not JSON; the status line is enough.
  }
  return fallback;
}

/* ----------------------------------------------------------------------- ui */

function setLoading(isLoading, label) {
  pending += isLoading ? 1 : -1;
  const busy = pending > 0;
  ui.buttons.forEach((button) => {
    button.disabled = busy;
  });
  ui.status.textContent = busy ? `${label}...` : "";
}

function showResult(text) {
  ui.result.textContent = text;
  ui.result.hidden = false;
  ui.error.hidden = true;
}

function showError(message) {
  ui.error.textContent = message;
  ui.error.hidden = false;
  ui.result.hidden = true;
}

function clearAreas() {
  ui.result.hidden = true;
  ui.error.hidden = true;
}
