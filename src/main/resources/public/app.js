// Served by the Java server as application/javascript and executed by the browser.
document.addEventListener("DOMContentLoaded", () => {
  const status = document.getElementById("script-status");
  if (status) {
    status.textContent =
      "app.js was fetched in its own HTTP request and is running in the browser.";
  }
});
