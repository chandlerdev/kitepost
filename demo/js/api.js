/*
 * Kitepost API client: the one place the UI gets data from.
 *
 * TEST BUILD: every call goes to MockServer (js/mock-server.js), which runs in the browser.
 * LIVE BUILD: set USE_MOCK = false and the same calls go to the backend over HTTP.
 * In Next.js this file becomes lib/api.ts and the endpoints become app/api/... route handlers.
 * Request and response shapes are the same in both modes (see README.md).
 */
(function () {
  const USE_MOCK = true;
  const BASE = "/api";

  async function http(method, path, body) {
    let res;
    try {
      res = await fetch(BASE + path, { method, headers: body ? { "content-type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
    } catch (e) { throw new Error("Can't reach the Kitepost server. Check that it is running."); }
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `Request failed (HTTP ${res.status})`);
    return data;
  }
  const q = (o) => { const p = new URLSearchParams(); Object.entries(o || {}).forEach(([k, v]) => v != null && v !== "" && p.set(k, v)); const s = p.toString(); return s ? "?" + s : ""; };

  // name: [mock function, HTTP method, path builder, body builder]
  const routes = {
    status:         ["GET", () => "/status"],
    reference:      ["GET", () => "/reference"],
    strategy:       ["POST", () => "/strategy", (input) => input],
    campaigns:      ["GET", () => "/campaigns"],
    campaign:       ["GET", (id) => `/campaigns/${id}`],
    createCampaign: ["POST", () => "/campaigns", (c) => c],
    updateCampaign: ["PATCH", (id) => `/campaigns/${id}`, (id, patch) => patch],
    start:          ["POST", (id) => `/campaigns/${id}/start`],
    pause:          ["POST", (id) => `/campaigns/${id}/pause`],
    findMore:       ["POST", (id) => `/campaigns/${id}/more`, (id, add = 25) => ({ add })],
    leads:          ["GET", (filter) => "/leads" + q(filter)],
    lead:           ["GET", (id) => `/leads/${id}`],
    updateLead:     ["PATCH", (id) => `/leads/${id}`, (id, patch) => patch],
    approve:        ["POST", (id) => `/leads/${id}/approve`],
    approveMany:    ["POST", () => "/leads/approve", (ids) => ({ ids })],
    reject:         ["POST", (id) => `/leads/${id}/reject`],
    unapprove:      ["POST", (id) => `/leads/${id}/unapprove`],
    redraft:        ["POST", (id) => `/leads/${id}/redraft`],
    replies:        ["GET", () => "/replies"],
    updateReply:    ["PATCH", (id) => `/replies/${id}`, (id, patch) => patch],
    answer:         ["POST", (id) => `/replies/${id}/answer`, (id, body) => ({ body })],
    checkInbox:     ["POST", () => "/replies/check"],
    suppression:    ["GET", () => "/suppression"],
    suppress:       ["POST", () => "/suppression", (value) => ({ value })],
    unsuppress:     ["DELETE", (value) => `/suppression/${encodeURIComponent(value)}`]
  };

  const Api = { isMock: USE_MOCK };
  for (const [name, [method, path, body]] of Object.entries(routes)) {
    Api[name] = (...args) => USE_MOCK
      ? window.MockServer.call(name, ...args)
      : http(method, path(...args), body ? body(...args) : undefined);
  }
  // test-build helpers
  Api.resetDemo = () => window.MockServer.reset();
  Api.exportData = () => window.MockServer.exportData();
  window.Api = Api;
})();
