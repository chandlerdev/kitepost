/*
 * Kitepost front end (talks to the backend through window.Api).
 * Views (URL hash):
 *   #campaigns          all campaigns
 *   #campaign-<id>      dashboard: categories, leads table (customers by default), activity
 *   #new                five-step wizard
 *   #review             approve drafted emails, see queued / sent / rejected
 *   #inbox              replies with intent, summary and answer
 *   #settings           how the server is configured, do-not-contact list
 */
(function () {
"use strict";

/* ---------- utilities ---------- */
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmt = (n) => Math.round(n || 0).toLocaleString("en-US");
const regionSummary = (list, label) => (!list || !list.length ? "" : list.length <= 2 ? list.join(" and ") : `${list.length} ${label}`);
const short = (s, n) => (s && s.length > n ? s.slice(0, n - 1) + "…" : s || "");
const ago = (iso) => {
  if (!iso) return "";
  const s = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  return s < 60 ? s + "s ago" : s < 3600 ? Math.round(s / 60) + "m ago" : s < 86400 ? Math.round(s / 3600) + "h ago" : Math.round(s / 86400) + "d ago";
};
let toastTimer;
const toast = (m, bad) => { const t = $("#toast"); t.textContent = m; t.style.background = bad ? "var(--bad)" : ""; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), bad ? 5000 : 2600); };
const busy = async (btn, fn) => { if (btn) btn.disabled = true; try { return await fn(); } catch (e) { toast(e.message, true); } finally { if (btn) btn.disabled = false; } };
const editing = () => { const a = document.activeElement; return a && /INPUT|TEXTAREA|SELECT/.test(a.tagName) && $("#main").contains(a); };

/* ---------- labels ---------- */
const CAT = { customer: "Customer", partner: "Partner", vendor: "Vendor", competitor: "Competitor", irrelevant: "Not relevant" };
const CAT_COLOR = { customer: "var(--ok)", partner: "var(--info)", vendor: "var(--warn)", competitor: "var(--bad)", irrelevant: "var(--line)" };
const ST = { analyzing: "Analysing", classified: "Marked", review: "Needs review", no_email: "No email found", suppressed: "Do not contact", approved: "Queued to send", sending: "Sending", sent: "Sent", replied: "Replied", bounced: "Bounced", failed: "Failed", rejected: "Rejected", skipped: "Unreadable site" };
const INTENT = { interested: "Interested", question: "Has questions", not_interested: "Not interested", unsubscribe: "Unsubscribe", out_of_office: "Out of office", bounce: "Bounced" };
const SRC = { claude: "Claude", rules: "keyword rules", you: "you", template: "template" };
const catPill = (c) => `<span class="pill c-${c || "none"}">${c ? CAT[c] : "Analysing"}</span>`;
const stPill = (s) => `<span class="pill st-${s}">${ST[s] || s}</span>`;

/* ---------- state ---------- */
const S = { ref: null, status: null, campaigns: [], cv: { cat: "customer", q: "" }, rv: { tab: "review", campaign: "all", selected: new Set(), dirty: new Set() }, iv: { sel: null }, wiz: null };
const countryName = (code) => S.ref.countries.find(c => c.code === code)?.name || code;
const langName = (l) => S.ref.languages[l] || l;

/* ---------- shell ---------- */
const ICON = {
  camp: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="2" y="3" width="12" height="10" rx="2"/><path d="M2 6.5h12"/></svg>',
  plus: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M8 3v10M3 8h10"/></svg>',
  review: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M3 8.5l3 3 7-7"/></svg>',
  inbox: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M2 9l2-6h8l2 6v4H2z"/><path d="M2 9h3.5l1 1.5h3l1-1.5H14"/></svg>',
  gear: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="8" cy="8" r="2.2"/><path d="M8 1.8v1.6M8 12.6v1.6M1.8 8h1.6M12.6 8h1.6M3.6 3.6l1.1 1.1M11.3 11.3l1.1 1.1M3.6 12.4l1.1-1.1M11.3 4.7l1.1-1.1"/></svg>'
};
function route() {
  const h = location.hash.slice(1);
  if (["new", "review", "inbox", "settings", "campaigns"].includes(h)) return { v: h };
  if (h.startsWith("campaign-")) return { v: "campaign", id: h.slice(9) };
  return S.campaigns.length ? { v: "campaign", id: S.campaigns[0].id } : { v: "campaigns" };
}
function renderNav() {
  const r = route(), st = S.status || {};
  const cur = (v) => (r.v === v ? ' aria-current="page"' : "");
  $("#nav").innerHTML = `
    <a href="#campaigns"${r.v === "campaigns" || r.v === "campaign" ? ' aria-current="page"' : ""}>${ICON.camp} Campaigns</a>
    <div class="nav-sub">${S.campaigns.slice(0, 8).map(c => `<a href="#campaign-${c.id}"${r.id === c.id ? ' style="color:var(--ink);font-weight:600"' : ""}><span class="dot${c.status === "running" ? " live" : ""}"></span>${esc(short(c.name, 25))}</a>`).join("")}</div>
    <a href="#new"${cur("new")}>${ICON.plus} New campaign</a>
    <a href="#review"${cur("review")}>${ICON.review} Review & send ${st.reviewCount ? `<span class="badge" style="background:var(--warn)">${st.reviewCount}</span>` : ""}</a>
    <a href="#inbox"${cur("inbox")}>${ICON.inbox} Replies ${st.unreadReplies ? `<span class="badge">${st.unreadReplies}</span>` : ""}</a>
    <a href="#settings"${cur("settings")}>${ICON.gear} Settings</a>`;
  const m = st.mode;
  if (m) $("#mode").innerHTML = `<span class="eyebrow">${m.browserOnly ? "Test build (simulated)" : "Server"}</span>
    <div><span>Search</span><b class="${m.search === "demo" ? "safe" : "live"}">${m.search === "demo" ? "Demo data" : m.search}</b></div>
    <div><span>Classifying</span><b class="${m.ai === "claude" ? "live" : "safe"}">${m.ai === "claude" ? "Claude" : "Keyword rules"}</b></div>
    <div><span>Email</span><b class="${m.email === "smtp" ? "live" : "safe"}">${m.email === "smtp" ? "Sending (SMTP)" : "Dry run"}</b></div>
    <div><span>Replies</span><b>${{ imap: "IMAP inbox", simulated: "Simulated", off: "Off" }[m.replies]}</b></div>
    <div><span>Sent today</span><b class="num">${fmt(st.sentToday)} / ${fmt(m.dailySendLimit)}</b></div>`;
}
const modeBanner = () => {
  const m = S.status?.mode; if (!m) return "";
  if (m.browserOnly) return `<div class="demo">Test build: everything runs in your browser with sample data. Companies are fictional and emails are recorded, not sent. Your changes are kept in this browser (Settings → Reset sample data).</div>`;
  const parts = [];
  if (m.search === "demo") parts.push("companies are fictional (.example)");
  if (m.email !== "smtp") parts.push("emails are recorded, not sent");
  if (m.replies === "simulated") parts.push("some replies are simulated");
  return parts.length ? `<div class="demo">Demo mode: ${parts.join(", ")}. See Settings to connect real search and email.</div>` : "";
};

async function refreshStatus() {
  try {
    const [st, cs] = await Promise.all([Api.status(), Api.campaigns()]);
    S.status = st; S.campaigns = cs; renderNav();
  } catch (e) { /* shown by views */ }
}

let viewToken = 0;
async function render() {
  const token = ++viewToken;
  closeDrawer();
  await refreshStatus();
  if (token !== viewToken) return;
  const r = route(), m = $("#main");
  try {
    if (r.v === "new") renderWizard();
    else if (r.v === "review") await renderReview();
    else if (r.v === "inbox") await renderInbox();
    else if (r.v === "settings") await renderSettings();
    else if (r.v === "campaign") await renderCampaign(r.id);
    else m.innerHTML = modeBanner() + campaignsHTML();
  } catch (e) {
    m.innerHTML = `<div class="load-error"><h2>This page couldn't load</h2><p>${esc(e.message)}</p></div>`;
  }
  window.scrollTo(0, 0);
}

/* ---------- campaigns list ---------- */
function campaignsHTML() {
  if (!S.campaigns.length) return `<div class="load-error"><h2>No campaigns yet</h2><p class="muted">Describe what you sell and who buys it. Kitepost finds those companies, sorts out partners, vendors and competitors, and drafts an email to each customer for you to approve.</p><a class="btn primary" href="#new" style="align-self:flex-start">${ICON.plus} Create your first campaign</a></div>`;
  return `<div class="head"><div><span class="eyebrow">Campaigns</span><h1>Your outreach</h1></div><a class="btn primary" href="#new">${ICON.plus} New campaign</a></div>
  <div class="list">${S.campaigns.map(c => `<a class="ccard" href="#campaign-${c.id}">
    <div style="display:flex;justify-content:space-between;gap:8px;align-items:center">${statusPill(c)}<span class="muted" style="font-size:.75rem">${c.countries.join(" · ")}</span></div>
    <h3>${esc(c.name)}</h3>
    <div class="row"><span class="muted">Customers found</span><b class="num">${fmt(c.stats.customer)}</b></div>
    <div class="row"><span class="muted">Waiting for review</span><b class="num">${fmt(c.stats.review)}</b></div>
    <div class="row"><span class="muted">Sent · replies</span><b class="num">${fmt(c.stats.sent)} · ${fmt(c.stats.replied)}</b></div></a>`).join("")}</div>`;
}
const statusPill = (c) => {
  const m = { running: ["s-sent", "Searching"], paused: ["s-queued", "Paused"], done: ["s-manual", "Search finished"], draft: ["s-queued", "Draft"] }[c.status] || ["s-queued", c.status];
  return `<span class="pill ${m[0]}"><span class="dot${c.status === "running" ? " live" : ""}"></span>${m[1]}</span>`;
};

/* ---------- campaign dashboard ---------- */
async function renderCampaign(id) {
  const [c, leads] = await Promise.all([Api.campaign(id), Api.leads({ campaign: id, category: S.cv.cat, q: S.cv.q })]);
  S.cv.id = id;
  $("#main").innerHTML = modeBanner() + `
  <div class="head">
    <div style="display:flex;flex-direction:column;gap:6px;min-width:0"><span class="eyebrow">${esc(c.goal)} campaign · ${c.countries.map(countryName).join(", ")}</span><h1>${esc(c.name)}</h1><p class="muted" style="max-width:72ch">${esc(c.description)}</p></div>
    <div class="actions" id="cactions"></div>
  </div>
  ${c.error ? `<div class="banner-err">${esc(c.error)}</div>` : ""}
  <div id="live-top"></div>
  <div class="split">
    <section class="panel">
      <div class="head"><div><h2>Leads</h2><p class="muted" style="font-size:.8rem">Every website is read and compared with your campaign. Only customers get an email, and only after you approve it.</p></div></div>
      <div class="toolbar"><div class="seg" id="catseg" role="group" aria-label="Show category"></div>
        <input type="text" id="q" placeholder="Search company, domain, email" value="${esc(S.cv.q)}" aria-label="Search leads" style="margin-left:auto">
      </div>
      <div class="tbl-wrap"><table><thead><tr><th>Company</th><th>Location</th><th>Fit</th><th>Category</th><th>Status</th></tr></thead><tbody id="rows"></tbody></table></div>
      <p class="hint" id="tcount"></p>
    </section>
    <section class="panel"><div class="head"><h2>Activity</h2><span id="runpill"></span></div><ul class="feed" id="feed"></ul></section>
  </div>`;
  paintCampaign(c, leads);
  $("#q").oninput = debounce(async (e) => { S.cv.q = e.target.value; paintRows(await Api.leads({ campaign: id, category: S.cv.cat, q: S.cv.q })); }, 250);
  $("#rows").onclick = (e) => { const tr = e.target.closest("tr[data-id]"); if (tr) openLead(tr.dataset.id); };
  $("#rows").onkeydown = (e) => { if (e.key === "Enter") { const tr = e.target.closest("tr[data-id]"); if (tr) openLead(tr.dataset.id); } };
}
function debounce(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

function paintCampaign(c, leads) {
  const s = c.stats;
  // actions
  const act = $("#cactions");
  if (act) {
    act.innerHTML = `${c.status === "running" ? `<button class="btn" id="pause">Pause search</button>` : c.status === "done" ? `<button class="btn" id="more">Find 25 more customers</button>` : `<button class="btn primary" id="resume">Resume search</button>`}
      <button class="btn" id="sendtoggle">${c.sending === false ? "Resume sending" : "Pause sending"}</button>
      <a class="btn ${s.review ? "primary" : ""}" href="#review">Review ${s.review ? `(${s.review})` : ""}</a>`;
    if ($("#pause")) $("#pause").onclick = (e) => busy(e.target, async () => { await Api.pause(c.id); toast("Search paused"); refreshCampaign(); });
    if ($("#resume")) $("#resume").onclick = (e) => busy(e.target, async () => { await Api.start(c.id); toast("Search resumed"); refreshCampaign(); });
    if ($("#more")) $("#more").onclick = (e) => busy(e.target, async () => { await Api.findMore(c.id, 25); toast("Looking for 25 more customers"); refreshCampaign(); });
    $("#sendtoggle").onclick = (e) => busy(e.target, async () => { await Api.updateCampaign(c.id, { sending: c.sending === false }); toast(c.sending === false ? "Sending resumed" : "Sending paused. Approved emails wait in the queue."); refreshCampaign(); });
  }
  // KPIs + categories + progress
  const totalCls = s.customer + s.partner + s.vendor + s.competitor + s.irrelevant;
  const cur = c.current;
  $("#live-top").innerHTML = `<div style="display:flex;flex-direction:column;gap:12px">
    <div class="kpis five">
      <div class="kpi"><span class="eyebrow">Websites analysed</span><span class="v">${fmt(s.analysed)}</span><span class="d">${fmt(s.found - s.analysed)} in progress · limit ${fmt(c.maxSites)}</span></div>
      <div class="kpi"><span class="eyebrow">Customers</span><span class="v" style="color:var(--ok)">${fmt(s.customer)}</span><span class="d">target ${fmt(c.targetCustomers)}</span></div>
      <div class="kpi"><span class="eyebrow">Waiting for review</span><span class="v" style="color:${s.review ? "var(--warn)" : "inherit"}">${fmt(s.review)}</span><span class="d">${fmt(s.noEmail)} without an email address</span></div>
      <div class="kpi"><span class="eyebrow">Sent</span><span class="v">${fmt(s.sent)}</span><span class="d">${fmt(s.approved)} queued to send</span></div>
      <div class="kpi"><span class="eyebrow">Replies</span><span class="v" style="color:var(--reply)">${fmt(s.replied)}</span><span class="d">${s.sent ? ((s.replied / s.sent) * 100).toFixed(0) : 0}% reply rate${s.bounced ? ` · ${s.bounced} bounced` : ""}</span></div>
    </div>
    <div class="panel" style="padding:14px 16px;gap:10px">
      <div class="head"><h3>Who we found</h3><span class="sumline">${statusPill(c)} ${cur ? `<span style="margin-left:6px">Now: ${esc(cur.location)}, ${esc(countryName(cur.country))}</span>` : ""}</span></div>
      <div class="catbar" aria-hidden="true">${Object.keys(CAT).map(k => s[k] ? `<span style="width:${(s[k] / Math.max(1, totalCls)) * 100}%;background:${CAT_COLOR[k]}"></span>` : "").join("")}</div>
      <div class="catlegend">${Object.keys(CAT).map(k => `<button data-cat="${k}"><i style="background:${CAT_COLOR[k]}"></i>${CAT[k]} <b>${fmt(s[k])}</b></button>`).join("")}${s.unreadable ? `<span>Unreadable sites <b>${fmt(s.unreadable)}</b></span>` : ""}</div>
      <div class="countries">${c.countries.map(code => { const now = c.status === "running" && cur && cur.country === code; const done = !now && (c.searched || []).includes(code); return `<span class="cty ${done ? "done" : now ? "now" : ""}">${done ? "✓" : now ? "●" : "·"} ${esc(countryName(code))}${c.regions?.[code]?.length ? ` (${esc(regionSummary(c.regions[code], code === "IN" ? "states/UTs" : "states"))})` : ""}</span>`; }).join("")}</div>
    </div></div>`;
  $$("[data-cat]").forEach(b => b.onclick = () => setCat(b.dataset.cat));
  // segmented control
  const counts = { ...Object.fromEntries(Object.keys(CAT).map(k => [k, s[k]])), all: s.found };
  $("#catseg").innerHTML = ["customer", "partner", "vendor", "competitor", "irrelevant", "all"].map(k => `<button data-seg="${k}" aria-pressed="${S.cv.cat === k}">${k === "all" ? "All" : CAT[k] + (k === "customer" ? "s" : k === "irrelevant" ? "" : "s")} <span class="n">${fmt(counts[k])}</span></button>`).join("");
  $$("[data-seg]").forEach(b => b.onclick = () => setCat(b.dataset.seg));
  // feed
  $("#feed").innerHTML = (c.activity || []).slice(0, 40).map(a => {
    const tone = { customer: "ok", sent: "ok", approved: "info", reply: "reply", competitor: "bad", vendor: "warn", partner: "info", error: "bad", skip: "warn" }[a.kind];
    const g = { customer: "★", sent: "✓", approved: "✓", reply: "↩", competitor: "×", vendor: "◇", partner: "◆", irrelevant: "–", error: "!", skip: "–", search: "⌕", info: "›" }[a.kind] || "›";
    const style = tone ? `background:var(--${tone}-soft);color:var(--${tone})` : "background:var(--sunk);color:var(--muted)";
    return `<li><span class="i" style="${style}">${g}</span><div>${esc(a.text)}</div><span class="t">${ago(a.at)}</span></li>`;
  }).join("") || `<li><span></span><div class="muted">Nothing yet.</div><span></span></li>`;
  $("#runpill").innerHTML = statusPill(c);
  paintRows(leads);
}
async function setCat(k) { S.cv.cat = k; const c = await Api.campaign(S.cv.id); const l = await Api.leads({ campaign: S.cv.id, category: k, q: S.cv.q }); paintCampaign(c, l); }

function paintRows({ leads, total }) {
  const tb = $("#rows"); if (!tb) return;
  tb.innerHTML = leads.map(l => {
    const sc = Number(l.score) || 0, col = sc >= 8 ? "var(--ok)" : sc >= 6 ? "var(--info)" : "var(--warn)";
    return `<tr data-id="${l.id}" tabindex="0">
      <td><div style="font-weight:600">${esc(l.company)}</div><span class="mono muted">${esc(l.email || l.domain)}</span></td>
      <td>${esc(l.location && l.location !== countryName(l.country) ? l.location + ", " : "")}<span class="flag">${l.country}</span></td>
      <td>${l.category === "customer" ? `<span class="score" style="--w:${sc * 10}%;--c:${col}"><i></i>${sc.toFixed(1)}</span>` : '<span class="muted">—</span>'}</td>
      <td>${catPill(l.category)}${l.categorySource === "you" ? '<span class="why">set by you</span>' : ""}</td>
      <td>${stPill(l.status)}${l.reason && l.category !== "customer" ? `<span class="why">${esc(short(l.reason, 70))}</span>` : ""}</td></tr>`;
  }).join("") || `<tr><td colspan="5" class="muted" style="text-align:center;padding:30px">${S.cv.cat === "customer" ? "No customers yet. They appear here as websites are analysed." : "Nothing in this category yet."}</td></tr>`;
  $("#tcount").textContent = total > leads.length ? `Showing ${leads.length} of ${total}` : `${total} ${total === 1 ? "lead" : "leads"}`;
}
async function refreshCampaign() {
  if (route().v !== "campaign" || !S.cv.id) return;
  const [c, l] = await Promise.all([Api.campaign(S.cv.id), Api.leads({ campaign: S.cv.id, category: S.cv.cat, q: S.cv.q })]);
  if (editing()) { paintRows(l); return; }
  paintCampaign(c, l);
}

/* ---------- lead drawer ---------- */
async function openLead(lid) {
  let l;
  try { l = await Api.lead(lid); } catch (e) { return toast(e.message, true); }
  const d = $("#drawer");
  const editable = ["review", "no_email", "rejected", "failed"].includes(l.status) && l.category === "customer";
  const contacted = ["sending", "sent", "replied", "bounced"].includes(l.status);
  d.innerHTML = `<header><div style="min-width:0"><span class="eyebrow">${esc(l.location && l.location !== countryName(l.country) ? l.location + ", " : "")}${esc(countryName(l.country))} · ${esc(langName(l.lang) || "")}</span><h2>${esc(l.company)}</h2><a class="mono muted" href="${esc(l.url)}" target="_blank" rel="noopener">${esc(l.domain)} ↗</a></div><button class="btn ghost" id="dclose" aria-label="Close">✕</button></header>
  <div class="body">
    <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">${catPill(l.category)}${stPill(l.status)}${l.category === "customer" ? `<span class="pill s-queued">Fit ${(Number(l.score) || 0).toFixed(1)} / 10</span>` : ""}</div>
    <section style="display:flex;flex-direction:column;gap:8px">
      <h3>Why this category</h3>
      <p>${esc(l.reason || l.error || "Still analysing this website.")}</p>
      <p class="hint">Decided by ${SRC[l.categorySource] || "—"}${l.confidence ? ` · ${Math.round(l.confidence * 100)}% confident` : ""}${l.industry ? ` · ${esc(l.industry)}` : ""}</p>
      ${!contacted && l.status !== "analyzing" ? `<div class="field"><label for="dcat">Change category</label><select id="dcat">${Object.keys(CAT).map(k => `<option value="${k}" ${k === l.category ? "selected" : ""}>${CAT[k]}</option>`).join("")}</select></div>` : ""}
    </section>
    ${l.siteSummary ? `<section style="display:flex;flex-direction:column;gap:6px"><h3>From their website</h3><p class="muted" style="font-size:.85rem">${esc(short(l.siteSummary, 420))}</p></section>` : ""}
    ${l.category === "customer" ? `<section style="display:flex;flex-direction:column;gap:10px">
      <h3>${contacted ? "Email sent" : "Email for your review"}</h3>
      ${contacted && l.sent ? `<dl class="kv"><dt>To</dt><dd class="mono">${esc(l.sent.to)}</dd><dt>Sent</dt><dd>${new Date(l.sent.at).toLocaleString()} ${l.sent.dryRun ? '<span class="pill s-queued">Dry run</span>' : ""}</dd><dt>Subject</dt><dd>${esc(l.sent.subject)}</dd></dl><div class="preview-mail">${esc(l.sent.text)}</div>${l.status === "replied" ? `<a class="btn primary" href="#inbox">Open reply</a>` : ""}` : `
      <div class="field"><label for="demail">Send to</label><input type="email" id="demail" value="${esc(l.email || "")}" placeholder="name@company.com" ${editable || l.status === "approved" ? "" : "disabled"}>${l.emails?.length > 1 ? `<span class="hint">Also found: ${l.emails.filter(e => e !== l.email).map(esc).join(", ")}</span>` : ""}</div>
      ${l.draft ? `<div class="field"><label for="dsubj">Subject</label><input type="text" id="dsubj" value="${esc(l.draft.subject)}" ${editable ? "" : "disabled"}></div>
      <div class="field"><label for="dbody">Message <span class="muted" style="font-weight:400">· ${esc(langName(l.draft.language))}, written by ${SRC[l.draft.source] || l.draft.source}</span></label><textarea id="dbody" style="min-height:230px" ${editable ? "" : "disabled"}>${esc(l.draft.body)}</textarea></div>
      <div class="footer-note" style="font-size:.75rem;color:var(--muted);white-space:pre-wrap;background:var(--sunk);border-radius:8px;padding:8px 10px">Added when sending:${esc(l.footer)}</div>` : `<p class="muted">${l.status === "no_email" ? "No email address was found on the website. Add one above to get a draft." : ""}</p>`}
      <div class="qactions">
        ${editable && l.draft ? `<button class="btn primary" id="dapprove">Approve & queue</button><button class="btn" id="dsave">Save changes</button><button class="btn" id="dredraft">Rewrite</button><button class="btn ghost" id="dreject">Reject</button>` : ""}
        ${editable && !l.draft ? `<button class="btn primary" id="dsave">Save email</button>` : ""}
        ${l.status === "approved" ? `<button class="btn" id="dunapprove">Move back to review</button>` : ""}
      </div>`}
    </section>` : ""}
  </div>`;
  d.hidden = false; $("#scrim").hidden = false;
  $("#dclose").onclick = closeDrawer; $("#dclose").focus();
  const after = async (msg) => { toast(msg); closeDrawer(); await refreshStatus(); route().v === "campaign" ? refreshCampaign() : render(); };
  const collect = () => ({ email: $("#demail")?.value.trim(), draft: $("#dsubj") ? { subject: $("#dsubj").value, body: $("#dbody").value } : undefined });
  if ($("#dcat")) $("#dcat").onchange = (e) => busy(e.target, async () => { await Api.updateLead(l.id, { category: e.target.value }); toast(`Marked as ${CAT[e.target.value]}`); openLead(l.id); refreshCampaign(); refreshStatus(); });
  if ($("#dsave")) $("#dsave").onclick = (e) => busy(e.target, async () => { await Api.updateLead(l.id, collect()); toast("Saved"); openLead(l.id); });
  if ($("#dapprove")) $("#dapprove").onclick = (e) => busy(e.target, async () => { await Api.updateLead(l.id, collect()); await Api.approve(l.id); after("Approved. It will be sent shortly."); });
  if ($("#dreject")) $("#dreject").onclick = (e) => busy(e.target, async () => { await Api.reject(l.id); after("Rejected. This company won't be contacted."); });
  if ($("#dredraft")) $("#dredraft").onclick = (e) => busy(e.target, async () => { await Api.redraft(l.id); toast("New draft written"); openLead(l.id); });
  if ($("#dunapprove")) $("#dunapprove").onclick = (e) => busy(e.target, async () => { await Api.unapprove(l.id); after("Moved back to review"); });
}
function closeDrawer() { $("#drawer").hidden = true; $("#scrim").hidden = true; }

/* ---------- review & send ---------- */
const RTABS = [
  { id: "review", label: "To review", status: "review", stat: "review" },
  { id: "approved", label: "Queued", status: "approved,sending", stat: "approved" },
  { id: "sent", label: "Sent", status: "sent,replied,bounced", stat: "sent" },
  { id: "no_email", label: "No email", status: "no_email", stat: "noEmail" },
  { id: "rejected", label: "Rejected", status: "rejected,failed", stat: "rejected" }
];
function reviewCounts() {
  const list = S.rv.campaign === "all" ? S.campaigns : S.campaigns.filter(c => c.id === S.rv.campaign);
  return Object.fromEntries(RTABS.map(t => [t.id, list.reduce((a, c) => a + (c.stats[t.stat] || 0), 0)]));
}
async function renderReview() {
  const tab = RTABS.find(t => t.id === S.rv.tab);
  const { leads, total } = await Api.leads({ status: tab.status, campaign: S.rv.campaign === "all" ? "" : S.rv.campaign, category: "customer", limit: 200 });
  S.rv.leads = leads; S.rv.dirty.clear();
  S.rv.selected = new Set([...S.rv.selected].filter(id => leads.some(l => l.id === id)));
  const counts = reviewCounts(), m = S.status.mode;
  $("#main").innerHTML = modeBanner() + `
  <div class="head"><div><span class="eyebrow">Review & send</span><h1>Approve emails before they go out</h1><p class="muted">Only customers are listed here. Nothing is sent until you approve it. Approved emails go out one every ${m.sendIntervalSeconds}s, up to ${m.dailySendLimit} a day.</p></div>
    <select id="rcamp" aria-label="Campaign" style="width:auto"><option value="all">All campaigns</option>${S.campaigns.map(c => `<option value="${c.id}" ${S.rv.campaign === c.id ? "selected" : ""}>${esc(short(c.name, 40))}</option>`).join("")}</select></div>
  <div class="seg" role="group" aria-label="Show">${RTABS.map(t => `<button data-rtab="${t.id}" aria-pressed="${S.rv.tab === t.id}">${t.label} <span class="n" data-rcount="${t.id}">${fmt(counts[t.id])}</span></button>`).join("")}</div>
  ${S.rv.tab === "review" && leads.length ? `<div class="bulk"><label style="display:flex;gap:8px;align-items:center;font-weight:600"><input type="checkbox" class="check" id="selall"> Select all</label><span class="muted" id="selcount"></span><button class="btn primary" id="bulkapprove" style="margin-left:auto">Approve selected</button></div>` : ""}
  <div class="queue" id="queue">${leads.length ? leads.map(l => reviewCard(l, S.rv.tab)).join("") : `<div class="empty panel">${emptyText(S.rv.tab)}</div>`}</div>
  ${total > leads.length ? `<p class="hint">Showing the first ${leads.length} of ${total}.</p>` : ""}`;
  bindReview();
}
const emptyText = (t) => ({ review: "<b>Nothing to review</b><span>New customer emails appear here as campaigns find companies.</span>", approved: "<b>The send queue is empty</b><span>Approve emails in “To review” to queue them.</span>", sent: "<b>No emails sent yet</b>", no_email: "<b>Every customer has an email address</b>", rejected: "<b>Nothing rejected</b>" }[t]);
function reviewCard(l, tab) {
  const head = `<div class="qhead"><div style="min-width:0"><h3>${esc(l.company)}</h3><div class="qmeta"><a class="mono" href="${esc(l.url)}" target="_blank" rel="noopener">${esc(l.domain)} ↗</a><span>${esc(l.location && l.location !== countryName(l.country) ? l.location + ", " : "")}${esc(countryName(l.country))}</span>${l.category === "customer" ? `<span>Fit ${(Number(l.score) || 0).toFixed(1)}</span>` : ""}</div></div>${stPill(l.status)}</div>
    <p class="sumline">${esc(l.reason || "")}</p>`;
  if (tab === "review") return `<article class="qcard${S.rv.selected.has(l.id) ? " sel" : ""}" data-lead="${l.id}">
    <input type="checkbox" class="check" data-sel="${l.id}" aria-label="Select ${esc(l.company)}" ${S.rv.selected.has(l.id) ? "checked" : ""}>
    <div class="qbody">${head}
      <div class="grid2"><div class="field"><label for="to-${l.id}">To</label><input type="email" id="to-${l.id}" data-f="email" value="${esc(l.email)}"></div>
      <div class="field"><label for="su-${l.id}">Subject</label><input type="text" id="su-${l.id}" data-f="subject" value="${esc(l.draft?.subject)}"></div></div>
      <div class="field"><label for="bo-${l.id}">Message <span class="muted" style="font-weight:400">· ${esc(langName(l.draft?.language))}, by ${SRC[l.draft?.source] || ""}</span></label><textarea id="bo-${l.id}" data-f="body">${esc(l.draft?.body)}</textarea></div>
      <div class="footer-note">Added when sending:${esc(l.footer)}</div>
      <div class="qactions"><button class="btn primary" data-act="approve">Approve & queue</button><button class="btn" data-act="save" hidden>Save changes</button><button class="btn" data-act="redraft">Rewrite</button><button class="btn" data-act="open">Details</button><button class="btn ghost" data-act="reject" style="margin-left:auto">Reject</button></div>
    </div></article>`;
  if (tab === "no_email") return `<article class="qcard" data-lead="${l.id}"><span></span><div class="qbody">${head}
      <div class="kw-add"><input type="email" id="ne-${l.id}" placeholder="Add an email address, e.g. sales@${esc(l.domain)}" aria-label="Email for ${esc(l.company)}"><button class="btn sm" data-act="addemail">Save and draft</button><button class="btn sm ghost" data-act="reject">Skip</button></div></div></article>`;
  if (tab === "approved") return `<article class="qcard" data-lead="${l.id}"><span></span><div class="qbody">${head}<p><b>${esc(l.draft?.subject)}</b> <span class="muted mono">→ ${esc(l.email)}</span></p><div class="qactions"><button class="btn" data-act="unapprove">Move back to review</button><button class="btn" data-act="open">Details</button></div></div></article>`;
  if (tab === "sent") return `<article class="qcard" data-lead="${l.id}"><span></span><div class="qbody">${head}<p><b>${esc(l.sent?.subject)}</b> <span class="muted mono">→ ${esc(l.sent?.to)}</span></p><p class="hint">${l.sent ? (l.sent.dryRun ? "Recorded (dry run) " : "Sent ") + ago(l.sent.at) : ""}${l.error ? " · " + esc(l.error) : ""}</p><div class="qactions">${l.status === "replied" ? `<a class="btn primary" href="#inbox">Open reply</a>` : ""}<button class="btn" data-act="open">View email</button></div></div></article>`;
  return `<article class="qcard" data-lead="${l.id}"><span></span><div class="qbody">${head}${l.error ? `<p class="err">${esc(l.error)}</p>` : ""}<div class="qactions"><button class="btn" data-act="open">Details</button>${l.draft && l.email ? `<button class="btn" data-act="approve">Approve & queue</button>` : ""}</div></div></article>`;
}
function bindReview() {
  $$("[data-rtab]").forEach(b => b.onclick = () => { S.rv.tab = b.dataset.rtab; S.rv.selected.clear(); renderReview(); });
  $("#rcamp").onchange = (e) => { S.rv.campaign = e.target.value; S.rv.selected.clear(); renderReview(); };
  const updSel = () => {
    if (!$("#selcount")) return;
    $("#selcount").textContent = S.rv.selected.size ? `${S.rv.selected.size} selected` : "";
    $("#bulkapprove").disabled = !S.rv.selected.size;
    $("#selall").checked = S.rv.selected.size && S.rv.selected.size === S.rv.leads.length;
  };
  updSel();
  if ($("#selall")) $("#selall").onchange = (e) => { S.rv.selected = new Set(e.target.checked ? S.rv.leads.map(l => l.id) : []); $$("[data-sel]").forEach(c => { c.checked = e.target.checked; c.closest(".qcard").classList.toggle("sel", e.target.checked); }); updSel(); };
  $$("[data-sel]").forEach(c => c.onchange = () => { c.checked ? S.rv.selected.add(c.dataset.sel) : S.rv.selected.delete(c.dataset.sel); c.closest(".qcard").classList.toggle("sel", c.checked); updSel(); });
  $$(".qcard [data-f]").forEach(inp => inp.oninput = () => { const card = inp.closest(".qcard"); S.rv.dirty.add(card.dataset.lead); card.querySelector('[data-act="save"]').hidden = false; });
  const save = async (card) => {
    const lid = card.dataset.lead; if (!S.rv.dirty.has(lid)) return;
    await Api.updateLead(lid, { email: card.querySelector('[data-f="email"]').value.trim(), draft: { subject: card.querySelector('[data-f="subject"]').value, body: card.querySelector('[data-f="body"]').value } });
    S.rv.dirty.delete(lid);
  };
  if ($("#bulkapprove")) $("#bulkapprove").onclick = (e) => busy(e.target, async () => {
    for (const card of $$(".qcard")) if (S.rv.selected.has(card.dataset.lead)) await save(card);
    const r = await Api.approveMany([...S.rv.selected]);
    S.rv.selected.clear();
    toast(`${r.approved} approved${r.skipped.length ? `, ${r.skipped.length} skipped: ${r.skipped[0].reason}` : ""}`, r.skipped.length > 0);
    await refreshStatus(); renderReview();
  });
  $("#queue").onclick = (e) => {
    const b = e.target.closest("[data-act]"); if (!b) return;
    const card = b.closest(".qcard"), lid = card.dataset.lead, act = b.dataset.act;
    const done = async (msg) => { toast(msg); await refreshStatus(); renderReview(); };
    if (act === "open") return openLead(lid);
    busy(b, async () => {
      if (act === "save") { await save(card); b.hidden = true; toast("Saved"); }
      if (act === "approve") { if (card.querySelector("[data-f]")) await save(card); await Api.approve(lid); await done("Approved. It will be sent shortly."); }
      if (act === "reject") { await Api.reject(lid); await done("Rejected"); }
      if (act === "unapprove") { await Api.unapprove(lid); await done("Moved back to review"); }
      if (act === "redraft") { if (S.rv.dirty.has(lid)) S.rv.dirty.delete(lid); await Api.redraft(lid); await done("New draft written"); }
      if (act === "addemail") { const v = $("#ne-" + lid).value.trim(); if (!v) return toast("Type an email address first.", true); await Api.updateLead(lid, { email: v }); await done("Saved. The draft is waiting in “To review”."); }
    });
  };
}
async function refreshReview() {
  const counts = reviewCounts();
  RTABS.forEach(t => { const el = document.querySelector(`[data-rcount="${t.id}"]`); if (el) el.textContent = fmt(counts[t.id]); });
  // reload the list only when nothing is being edited or selected
  if (!editing() && !S.rv.dirty.size && !S.rv.selected.size && counts[S.rv.tab] !== (S.rv.leads || []).length) renderReview();
}

/* ---------- inbox ---------- */
async function renderInbox() {
  const list = await Api.replies();
  S.iv.list = list;
  if (!S.iv.sel || !list.some(r => r.id === S.iv.sel)) S.iv.sel = list[0]?.id;
  const r = list.find(x => x.id === S.iv.sel);
  if (r && r.unread) { Api.updateReply(r.id, { unread: false }).then(refreshStatus); r.unread = false; }
  const m = S.status.mode;
  $("#main").innerHTML = modeBanner() + `
  <div class="head"><div><span class="eyebrow">Replies</span><h1>Replies to your emails</h1><p class="muted">${m.replies === "imap" ? "Your inbox is checked regularly. Each reply is matched to the email it answers." : m.replies === "simulated" ? "Demo replies arrive a little while after an email is recorded." : "Reply tracking is off. Add IMAP settings to track replies."}</p></div>
    ${m.replies === "imap" ? `<button class="btn" id="checknow">Check inbox now</button>` : ""}</div>
  <div class="inbox">
    <div class="threads">${list.length ? list.map(x => `<button class="thread" data-r="${x.id}" aria-current="${x.id === S.iv.sel}">
      <span class="r1"><b>${esc(short(x.fromName, 34))}</b>${x.handled ? '<span class="handled">Answered</span>' : x.unread ? '<span class="new">New</span>' : `<span class="muted" style="font-size:.72rem">${ago(x.receivedAt)}</span>`}</span>
      <span class="r1"><span class="mono muted">${esc(x.domain)}</span><span class="pill i-${x.intent}" style="font-size:.68rem">${INTENT[x.intent]}</span></span><span class="sn">${esc(x.summary)}</span></button>`).join("")
      : `<div class="empty"><b>No replies yet</b><span>Replies appear here when companies answer your emails.</span></div>`}</div>
    <div class="read">${r ? replyDetail(r) : `<div class="empty">Select a reply</div>`}</div>
  </div>`;
  $$("[data-r]").forEach(b => b.onclick = () => { S.iv.sel = b.dataset.r; renderInbox(); });
  if ($("#checknow")) $("#checknow").onclick = (e) => busy(e.target, async () => { await Api.checkInbox(); toast("Inbox checked"); renderInbox(); });
  if (!r) return;
  if ($("#ans")) $("#ans").oninput = debounce(() => Api.updateReply(r.id, { draft: $("#ans").value }), 600);
  if ($("#sendans")) $("#sendans").onclick = (e) => busy(e.target, async () => { const res = await Api.answer(r.id, $("#ans").value); toast(res.dryRun ? "Answer recorded (dry run, not sent)" : "Answer sent"); renderInbox(); });
  if ($("#copyans")) $("#copyans").onclick = () => { const t = $("#ans"); try { navigator.clipboard.writeText(t.value).then(() => toast("Copied"), () => { t.select(); toast("Press Ctrl+C or ⌘C to copy"); }); } catch { t.select(); } };
  $("#handled").onclick = (e) => busy(e.target, async () => { await Api.updateReply(r.id, { handled: !r.handled }); renderInbox(); });
  if ($("#openlead")) $("#openlead").onclick = () => openLead(r.leadId);
}
function replyDetail(r) {
  const needsAnswer = ["interested", "question"].includes(r.intent);
  return `<div class="head"><div style="min-width:0"><h2>${esc(r.fromName)}</h2><span class="muted">${esc(r.company)} · <span class="mono">${esc(r.from)}</span> · ${ago(r.receivedAt)}</span></div><span class="pill i-${r.intent}">${INTENT[r.intent]}</span></div>
    <div class="summary"><b>Summary:</b> ${esc(r.summary)}${["unsubscribe", "not_interested"].includes(r.intent) ? " <br><b>Added to your do-not-contact list.</b>" : ""}</div>
    <div class="msg"><span class="eyebrow">Their reply${r.translation ? " · translated from " + esc(langName(r.lang)) : ""}${r.simulated ? " · demo" : ""}</span><div class="body">${esc(r.translation || r.text)}</div>
      ${r.translation ? `<details class="orig"><summary>Show original (${esc(langName(r.lang))})</summary><div class="body">${esc(r.text)}</div></details>` : ""}</div>
    ${r.sentEmail ? `<details class="orig"><summary>Your email (${esc(r.sentEmail.subject)})</summary><div class="body">${esc(r.sentEmail.text)}</div></details>` : ""}
    ${(r.answers || []).map(a => `<div class="msg"><span class="eyebrow">Your answer · ${ago(a.at)}${a.dryRun ? " · dry run" : ""}</span><div class="body">${esc(a.body)}</div></div>`).join("")}
    ${needsAnswer || r.draft ? `<div class="field draft"><label for="ans">${r.answers?.length ? "Another answer" : "Suggested answer"}, in ${esc(langName(r.lang))}</label><textarea id="ans">${esc(r.answers?.length ? "" : r.draft)}</textarea></div>
    <div class="qactions"><button class="btn primary" id="sendans">Send answer</button><button class="btn" id="copyans">Copy</button><button class="btn" id="handled">${r.handled ? "Mark as open" : "Mark as handled"}</button><button class="btn ghost" id="openlead">Lead details</button></div>`
    : `<div class="qactions"><button class="btn" id="handled">${r.handled ? "Mark as open" : "Mark as handled"}</button><button class="btn ghost" id="openlead">Lead details</button></div>`}
    <p class="hint">Answers are sent from your campaign's sender address, in the same email thread. Nothing is sent until you press Send answer.</p>`;
}
async function refreshInbox() {
  if (editing()) return;
  const list = await Api.replies();
  if (list.length !== (S.iv.list || []).length) renderInbox();
}

/* ---------- settings ---------- */
async function renderSettings() {
  const sup = await Api.suppression();
  const m = S.status.mode;
  if (m.browserOnly) return renderTestSettings(sup);
  const row = (label, on, now, how) => `<tr><td><b>${label}</b></td><td><span class="pill ${on ? "s-sent" : "s-skipped"}">${now}</span></td><td class="muted">${how}</td></tr>`;
  $("#main").innerHTML = modeBanner() + `
  <div><span class="eyebrow">Settings</span><h1>Server and sending</h1><p class="muted">These are read from the <span class="mono">.env</span> file on the server. Restart the server after changing it.</p></div>
  <section class="panel"><h2>Connections</h2><div class="tbl-wrap"><table style="min-width:560px"><thead><tr><th>Part</th><th>Now</th><th>To change</th></tr></thead><tbody>
    ${row("Finding companies", m.search !== "demo", m.search === "demo" ? "Demo data" : m.search, "Set BRAVE_API_KEY, SERPAPI_KEY, or GOOGLE_CSE_KEY + GOOGLE_CSE_CX")}
    ${row("Classifying & writing", m.ai === "claude", m.ai === "claude" ? "Claude · " + esc(m.aiModel) : "Keyword rules", "Set ANTHROPIC_API_KEY (and optionally ANTHROPIC_MODEL)")}
    ${row("Sending email", m.email === "smtp", m.email === "smtp" ? "SMTP" : "Dry run", "Set SMTP_HOST, SMTP_USER, SMTP_PASS and DRY_RUN=false")}
    ${row("Tracking replies", m.replies === "imap", { imap: "IMAP", simulated: "Simulated", off: "Off" }[m.replies], "Set IMAP_HOST (uses SMTP_USER/PASS unless IMAP_USER/PASS are set)")}
    ${row("Send pace", true, `1 per ${m.sendIntervalSeconds}s · ${m.dailySendLimit}/day`, "SEND_INTERVAL_SECONDS and DAILY_SEND_LIMIT")}
  </tbody></table></div></section>
  <section class="panel"><div><h2>Do-not-contact list</h2><p class="muted">Emails and domains here are never contacted. People who reply “unsubscribe” or “not interested” are added automatically.</p></div>
    <div class="kw-add"><input type="text" id="supv" placeholder="name@company.com or company.com" aria-label="Email or domain"><button class="btn" id="supadd">Add</button></div>
    <div class="listbox">${sup.length ? sup.map(s => `<div class="sup"><span><span class="mono">${esc(s.value)}</span> <span class="muted">· ${esc(s.reason)} · ${ago(s.at)}</span></span><button class="btn sm ghost" data-unsup="${esc(s.value)}">Remove</button></div>`).join("") : `<div class="sup muted">Nobody on the list yet.</div>`}</div>
  </section>`;
  $("#supadd").onclick = (e) => busy(e.target, async () => { await Api.suppress($("#supv").value); toast("Added"); renderSettings(); });
  $$("[data-unsup]").forEach(b => b.onclick = () => busy(b, async () => { await Api.unsuppress(b.dataset.unsup); toast("Removed"); renderSettings(); }));
}

function suppressionHTML(sup) {
  return `<section class="panel"><div><h2>Do-not-contact list</h2><p class="muted">Emails and domains here are never contacted. People who reply “unsubscribe” or “not interested” are added automatically.</p></div>
    <div class="kw-add"><input type="text" id="supv" placeholder="name@company.com or company.com" aria-label="Email or domain"><button class="btn" id="supadd">Add</button></div>
    <div class="listbox">${sup.length ? sup.map(s => `<div class="sup"><span><span class="mono">${esc(s.value)}</span> <span class="muted">· ${esc(s.reason)} · ${ago(s.at)}</span></span><button class="btn sm ghost" data-unsup="${esc(s.value)}">Remove</button></div>`).join("") : `<div class="sup muted">Nobody on the list yet.</div>`}</div>
  </section>`;
}
function bindSuppression() {
  $("#supadd").onclick = (e) => busy(e.target, async () => { await Api.suppress($("#supv").value); toast("Added"); renderSettings(); });
  $$("[data-unsup]").forEach(b => b.onclick = () => busy(b, async () => { await Api.unsuppress(b.dataset.unsup); toast("Removed"); renderSettings(); }));
}
function renderTestSettings(sup) {
  const m = S.status.mode;
  $("#main").innerHTML = modeBanner() + `
  <div><span class="eyebrow">Settings</span><h1>Test build</h1><p class="muted">This version has no server. A small simulator in the browser stands in for the backend and answers the same API calls.</p></div>
  <section class="panel"><h2>What is simulated</h2><div class="tbl-wrap"><table style="min-width:560px"><thead><tr><th>Part</th><th>In this test build</th><th>In the live app</th></tr></thead><tbody>
    <tr><td><b>Finding companies</b></td><td>Fictional companies on .example domains</td><td class="muted">Brave, SerpAPI or Google search</td></tr>
    <tr><td><b>Classifying & writing</b></td><td>Keyword rules and email templates</td><td class="muted">Claude reads each site and writes each email</td></tr>
    <tr><td><b>Sending</b></td><td>Recorded only, one every ${m.sendIntervalSeconds}s</td><td class="muted">Your SMTP mailbox</td></tr>
    <tr><td><b>Replies</b></td><td>About 4 in 10 emails get a simulated reply after 10–40s</td><td class="muted">Your IMAP inbox</td></tr>
  </tbody></table></div></section>
  <section class="panel"><div><h2>Sample data</h2><p class="muted">Start over with the example campaign, or copy everything as JSON to use as fixtures in the Next.js app.</p></div>
    <div class="qactions"><button class="btn" id="reset">Reset sample data</button><button class="btn" id="copyjson">Copy data as JSON</button><span class="muted" id="resetask" hidden>This removes your campaigns and changes in this browser. <button class="btn sm primary" id="resetyes">Reset</button> <button class="btn sm ghost" id="resetno">Cancel</button></span></div>
    <textarea id="jsonout" hidden readonly style="min-height:160px;font-family:var(--f-mono);font-size:.75rem" aria-label="Data as JSON"></textarea>
  </section>
  ${suppressionHTML(sup)}`;
  $("#reset").onclick = () => { $("#resetask").hidden = false; };
  $("#resetno").onclick = () => { $("#resetask").hidden = true; };
  $("#resetyes").onclick = (e) => busy(e.target, async () => { await Api.resetDemo(); S.cv = { cat: "customer", q: "" }; S.rv.selected.clear(); toast("Sample data restored"); location.hash = "campaigns"; render(); });
  $("#copyjson").onclick = () => {
    const t = $("#jsonout"); t.value = JSON.stringify(Api.exportData(), null, 2); t.hidden = false;
    try { navigator.clipboard.writeText(t.value).then(() => toast("Copied"), () => { t.select(); toast("Press Ctrl+C or ⌘C to copy"); }); } catch (e) { t.select(); }
  };
  bindSuppression();
}

/* ---------- wizard ---------- */
const STEPS = ["Your offer", "Where", "Customers", "Sender", "Review & start"];
function newWizard() {
  return { step: 0, errors: {}, loading: false, c: { name: "", description: "", goal: "Sales", countries: ["US", "IN"], regions: {}, strategy: null, strategyFor: "", sender: { name: "", company: "", email: "", website: "", role: "", phone: "", address: "" }, tone: "friendly", emailLanguage: "local", targetCustomers: 25, maxSites: 250 } };
}
function renderWizard() {
  if (!S.wiz) S.wiz = newWizard();
  const w = S.wiz, c = w.c, e = w.errors;
  const steps = `<ol class="steps">${STEPS.map((n, i) => `<li class="${i < w.step ? "done" : i === w.step ? "cur" : ""}">${n}</li>`).join("")}</ol>`;
  let body = "";
  if (w.step === 0) body = `
    <div class="head"><div><h2>Say what you sell, and to whom</h2><p class="muted">The campaign name and description decide which companies count as customers. Partners, vendors and competitors are marked and left out.</p></div><button class="btn ghost sm" id="example">Fill with an example</button></div>
    <div class="field"><label for="w-name">Campaign name</label><input type="text" id="w-name" value="${esc(c.name)}" placeholder="e.g. Dental scheduling software: US and India clinics">${e.name ? `<span class="err">${e.name}</span>` : ""}</div>
    <div class="field"><span class="lbl">Goal</span><div class="chips" data-group="goal">${S.ref.goals.map(g => `<button type="button" class="chip" data-v="${g}" aria-pressed="${c.goal === g}">${g}</button>`).join("")}</div></div>
    <div class="field"><label for="w-desc">Describe your offer and your customers</label><textarea id="w-desc" maxlength="1500" placeholder="We make … We want to reach … because …">${esc(c.description)}</textarea>
      <span class="hint"><span id="dcount">${c.description.length}</span> / 1,500. Say what you sell, who buys it, and anyone you don't want to reach.</span>${e.description ? `<span class="err">${e.description}</span>` : ""}</div>`;
  if (w.step === 1) {
    const groups = [...new Set(S.ref.countries.map(x => x.region))];
    const usOn = c.countries.includes("US");
    body = `<div><h2>Where should we look?</h2><p class="muted">Countries are searched in the order you pick them. Emails are written in each company's language unless you choose English below.</p></div>
    ${groups.map(g => `<div class="region-group"><span class="eyebrow">${g}</span><div class="chips" data-group="countries">${S.ref.countries.filter(x => x.region === g).map(x => `<button type="button" class="chip" data-v="${x.code}" aria-pressed="${c.countries.includes(x.code)}">${x.name}${c.countries.includes(x.code) && c.countries.length > 1 ? ` <span class="n">${c.countries.indexOf(x.code) + 1}</span>` : ""}</button>`).join("")}</div></div>`).join("")}
    ${e.countries ? `<span class="err">${e.countries}</span>` : ""}
    ${c.countries.filter(code => S.ref.regions[code]).map(code => { const reg = S.ref.regions[code], chosen = c.regions[code] || []; return `<div class="states"><div class="head"><div><b>${esc(countryName(code))}: which ${reg.label}?</b><p class="hint">${chosen.length ? `${chosen.length} selected. Each one is searched separately.` : `All ${reg.label}. Pick some to search one by one and get more local results.`}</p></div><button class="btn sm ghost" data-clear="${code}" ${chosen.length ? "" : "hidden"}>Use all ${reg.label}</button></div>
      <div class="chips" data-group="regions" data-country="${code}">${reg.list.map(s => `<button type="button" class="chip" data-v="${esc(s)}" aria-pressed="${chosen.includes(s)}">${esc(s)}</button>`).join("")}</div></div>`; }).join("")}
    <div class="field"><span class="lbl">Email language</span><div class="choice-row" data-group="emailLanguage">
      <button type="button" class="choice" data-v="local" aria-pressed="${c.emailLanguage === "local"}"><b>Each company's language</b><span>German for German sites, French for French sites.</span></button>
      <button type="button" class="choice" data-v="english" aria-pressed="${c.emailLanguage === "english"}"><b>Always English</b><span>One language for every email.</span></button></div></div>
    <div class="numrow"><div class="field"><label for="w-target">Stop after this many customers</label><input type="number" id="w-target" min="1" max="1000" value="${c.targetCustomers}"></div>
      <div class="field"><label for="w-max">Analyse at most this many websites</label><input type="number" id="w-max" min="10" max="5000" value="${c.maxSites}"><span class="hint">Limits search and AI costs.</span></div></div>`;
  }
  if (w.step === 2) {
    const st = c.strategy;
    body = `<div class="head"><div><h2>Who counts as a customer</h2><p class="muted">Built from your campaign name and description. Websites matching these profiles become customers. Others are marked as partner, vendor, competitor or not relevant.</p></div>${st ? `<button class="btn sm" id="regen">Rebuild</button>` : ""}</div>
    ${w.loading ? `<div class="gen"><b>Working out your customers</b><ul><li class="ok"><span class="ic"><span class="spin"></span></span>Reading your description and building search keywords…</li></ul></div>` :
      e.strategy ? `<div class="banner-err">${esc(e.strategy)}</div>` : st ? `
      <p class="sumline">You sell: <b style="color:var(--ink)">${esc(st.offer)}</b> · built with ${st.source === "claude" ? "Claude" : "keyword rules (add ANTHROPIC_API_KEY for better results)"}</p>
      <div class="personas">${st.profiles.map(p => `<div class="persona${p.on ? "" : " off"}">
        <header><div><h3>${esc(p.name)}</h3><p class="muted" style="font-size:.8rem">${esc(p.why)}</p></div><input type="checkbox" class="switch" aria-label="Use ${esc(p.name)}" ${p.on ? "checked" : ""} data-toggle="${p.id}"></header>
        <div class="chips">${p.keywords.map((k, i) => `<span class="chip kw">${esc(k)}<button type="button" aria-label="Remove ${esc(k)}" data-rm="${p.id}:${i}">×</button></span>`).join("")}</div>
        <div class="kw-add"><input type="text" placeholder="Add a search phrase" id="add-${p.id}" aria-label="Add keyword to ${esc(p.name)}"><button class="btn sm" data-add="${p.id}">Add</button></div></div>`).join("")}
        <div class="persona"><h3>Add a customer type</h3><div class="kw-add"><input type="text" id="newprof" placeholder="e.g. Hospital procurement teams" aria-label="New customer type"><button class="btn sm" id="addprof">Add</button></div></div></div>
      ${Object.keys(st.localKeywords || {}).length ? `<p class="hint">Also searching in local languages: ${Object.entries(st.localKeywords).map(([l, k]) => `${esc(langName(l))} (${k.length} phrases)`).join(", ")}.</p>` : ""}
      ${e.profiles ? `<span class="err">${e.profiles}</span>` : ""}` : ""}`;
  }
  if (w.step === 3) {
    const usOn = c.countries.includes("US");
    body = `<div><h2>Who is sending?</h2><p class="muted">Used to sign each email. Replies go to this address.</p></div>
    <div class="grid2">${[["name", "Full name", "text", true], ["company", "Company", "text", true], ["email", "Email (replies come here)", "email", true], ["role", "Position or role", "text", false], ["website", "Website", "url", false], ["phone", "Phone", "tel", false]]
      .map(([k, l, t, req]) => `<div class="field"><label for="s-${k}">${l}${req ? " *" : ""}</label><input type="${t}" id="s-${k}" data-s="${k}" value="${esc(c.sender[k])}">${e[k] ? `<span class="err">${e[k]}</span>` : ""}</div>`).join("")}</div>
    <div class="field"><label for="s-address">Postal address${usOn ? " *" : ""}</label><input type="text" id="s-address" data-s="address" value="${esc(c.sender.address)}" placeholder="Street, city, postcode, country"><span class="hint">${usOn ? "Required for US recipients by the CAN-SPAM Act. It's added to the footer of every email." : "Recommended. Added to the footer of every email with an unsubscribe line."}</span>${e.address ? `<span class="err">${e.address}</span>` : ""}</div>
    <div class="field"><span class="lbl">How should your emails sound?</span><div class="choice-row" data-group="tone">${S.ref.tones.map(t => `<button type="button" class="choice" data-v="${t.id}" aria-pressed="${c.tone === t.id}"><b>${t.label}</b><span>${t.desc}</span></button>`).join("")}</div></div>`;
  }
  if (w.step === 4) {
    body = `<div><h2>Ready to start</h2><p class="muted">Starting only searches and drafts. Every email waits for your approval in Review & send.</p></div>
    ${e.submit ? `<div class="banner-err">${esc(e.submit)}</div>` : ""}
    <dl class="review">
      <dt>Campaign</dt><dd><b>${esc(c.name)}</b> · ${esc(c.goal)}</dd>
      <dt>You sell</dt><dd>${esc(c.strategy?.offer)}</dd>
      <dt>Customers</dt><dd>${c.strategy?.profiles.filter(p => p.on).map(p => esc(p.name)).join(", ")}</dd>
      <dt>Countries</dt><dd>${c.countries.map(code => esc(countryName(code)) + (c.regions[code]?.length ? ` (${esc(regionSummary(c.regions[code], S.ref.regions[code]?.label || "regions"))})` : "")).join(" → ")}</dd>
      <dt>Limits</dt><dd>Stop at ${fmt(c.targetCustomers)} customers or ${fmt(c.maxSites)} websites</dd>
      <dt>Emails</dt><dd>${c.emailLanguage === "local" ? "In each company's language" : "In English"} · ${esc(c.tone)} tone</dd>
      <dt>Sender</dt><dd>${esc(c.sender.name)}, ${esc(c.sender.company)} · <span class="mono">${esc(c.sender.email)}</span></dd>
      <dt>Sending</dt><dd>${S.status.mode.email === "smtp" ? `Live SMTP, one email every ${S.status.mode.sendIntervalSeconds}s after approval` : "Dry run: approved emails are recorded, not sent"}</dd>
    </dl>`;
  }
  const foot = `<div class="wiz-foot"><button class="btn" id="back" ${w.step === 0 ? "disabled" : ""}>Back</button><button class="btn primary" id="next" ${w.loading ? "disabled" : ""}>${w.step === 4 ? "Start campaign" : "Continue"}</button></div>`;
  $("#main").innerHTML = modeBanner() + `<div class="wiz"><div><span class="eyebrow">New campaign</span><h1>Set up your outreach</h1></div>${steps}<section class="panel">${body}</section>${foot}</div>`;
  bindWizard();
}
async function loadStrategy(force) {
  const w = S.wiz, c = w.c, key = JSON.stringify([c.name, c.description, c.goal, c.countries]);
  if (!force && c.strategy && c.strategyFor === key) return;
  w.loading = true; w.errors = {}; renderWizard();
  try { c.strategy = await Api.strategy({ name: c.name, description: c.description, goal: c.goal, countries: c.countries }); c.strategyFor = key; }
  catch (e) { w.errors.strategy = e.message; c.strategy = null; }
  w.loading = false;
  if (route().v === "new") renderWizard();
}
function bindWizard() {
  const w = S.wiz, c = w.c;
  const keep = (fn) => () => { fn(); renderWizard(); };
  $$("[data-group]").forEach(g => g.addEventListener("click", (ev) => {
    const b = ev.target.closest("[data-v]"); if (!b) return;
    const k = g.dataset.group, v = b.dataset.v;
    if (k === "countries") { c.countries = c.countries.includes(v) ? c.countries.filter(x => x !== v) : [...c.countries, v]; }
    else if (k === "regions") { const code = g.dataset.country, cur = c.regions[code] || []; c.regions[code] = cur.includes(v) ? cur.filter(x => x !== v) : [...cur, v]; }
    else c[k] = v;
    renderWizard();
  }));
  if ($("#w-name")) $("#w-name").oninput = (e) => (c.name = e.target.value);
  if ($("#w-desc")) $("#w-desc").oninput = (e) => { c.description = e.target.value; $("#dcount").textContent = c.description.length; };
  if ($("#w-target")) $("#w-target").oninput = (e) => (c.targetCustomers = Number(e.target.value));
  if ($("#w-max")) $("#w-max").oninput = (e) => (c.maxSites = Number(e.target.value));
  $$("[data-clear]").forEach(b => (b.onclick = keep(() => (c.regions[b.dataset.clear] = []))));
  if ($("#example")) $("#example").onclick = keep(() => Object.assign(c, {
    name: "Dental scheduling software: US and India clinics", goal: "Sales",
    description: "We build ChairTime, online scheduling and reminder software for dental clinics. We are looking for independent dental practices, orthodontists and small dental groups that still book appointments by phone. We do not want dental suppliers, labs or other software companies."
  }));
  $$("[data-s]").forEach(i => (i.oninput = (e) => (c.sender[i.dataset.s] = e.target.value)));
  $$("[data-toggle]").forEach(t => (t.onchange = keep(() => (c.strategy.profiles.find(x => x.id === t.dataset.toggle).on = t.checked))));
  $$("[data-rm]").forEach(b => (b.onclick = keep(() => { const [pid, i] = b.dataset.rm.split(":"); c.strategy.profiles.find(x => x.id === pid).keywords.splice(+i, 1); })));
  $$("[data-add]").forEach(b => {
    const pid = b.dataset.add, inp = $("#add-" + pid);
    const add = () => { const v = inp.value.trim(); if (!v) return; c.strategy.profiles.find(x => x.id === pid).keywords.push(v); renderWizard(); $("#add-" + pid)?.focus(); };
    b.onclick = add; inp.onkeydown = (e) => { if (e.key === "Enter") add(); };
  });
  if ($("#addprof")) { const add = () => { const v = $("#newprof").value.trim(); if (!v) return; c.strategy.profiles.push({ id: "p" + Date.now(), name: v, why: "Added by you.", keywords: [v.toLowerCase()], on: true }); renderWizard(); }; $("#addprof").onclick = add; $("#newprof").onkeydown = (e) => { if (e.key === "Enter") add(); }; }
  if ($("#regen")) $("#regen").onclick = () => loadStrategy(true);
  $("#back").onclick = () => { w.step--; w.errors = {}; renderWizard(); };
  $("#next").onclick = async () => {
    const er = {};
    if (w.step === 0) { if (!c.name.trim()) er.name = "Give the campaign a name."; if (c.description.trim().length < 30) er.description = "Describe your offer and customers in at least 30 characters."; }
    if (w.step === 1) { if (!c.countries.length) er.countries = "Pick at least one country."; }
    if (w.step === 2) { if (!c.strategy) er.strategy = er.strategy || "Rebuild the customer profiles first."; else if (!c.strategy.profiles.some(p => p.on && p.keywords.length)) er.profiles = "Keep at least one customer type with a search phrase."; }
    if (w.step === 3) {
      if (!c.sender.name.trim()) er.name = "Add the name that signs your emails.";
      if (!c.sender.company.trim()) er.company = "Add your company name.";
      if (!/^\S+@\S+\.\S+$/.test(c.sender.email)) er.email = "Enter an email address where replies can reach you.";
      if (c.countries.includes("US") && !c.sender.address.trim()) er.address = "Add your postal address. It's required for emails to US companies.";
    }
    w.errors = er;
    if (Object.keys(er).length) return renderWizard();
    if (w.step === 1) { w.step = 2; return loadStrategy(false); }
    if (w.step < 4) { w.step++; return renderWizard(); }
    const btn = $("#next"); btn.disabled = true; btn.textContent = "Starting…";
    try {
      const created = await Api.createCampaign({ ...c, regions: Object.fromEntries(Object.entries(c.regions).filter(([k, v]) => c.countries.includes(k) && v.length)), start: true });
      S.wiz = null; S.cv.cat = "customer"; S.cv.q = "";
      toast("Campaign started. Customers appear as websites are analysed.");
      location.hash = "campaign-" + created.id;
    } catch (e) { w.errors = { submit: e.message }; renderWizard(); }
  };
}

/* ---------- polling ---------- */
async function poll() {
  try {
    await refreshStatus();
    const v = route().v;
    if (v === "campaign" && $("#drawer").hidden) await refreshCampaign();
    else if (v === "review") await refreshReview();
    else if (v === "inbox") await refreshInbox();
    else if (v === "campaigns") $("#main").innerHTML = modeBanner() + campaignsHTML();
  } catch (e) { /* keep polling */ }
}

/* ---------- boot ---------- */
(async function boot() {
  try { S.ref = await Api.reference(); }
  catch (e) {
    const file = location.protocol === "file:";
    $("#main").innerHTML = Api.isMock
      ? `<div class="load-error"><h2>The sample data could not load</h2><p class="muted">${esc(e.message)}</p>${file ? `<p>Browsers block reading the JSON files when a page is opened straight from disk. In this folder run <code>python3 -m http.server 8000</code> (or <code>npx serve .</code>) and open <code>http://localhost:8000</code>.</p>` : `<p>Check that the <code>data</code> folder sits next to <code>index.html</code>.</p>`}</div>`
      : `<div class="load-error"><h2>Can't reach the server</h2><p>${esc(e.message)}</p><p class="muted">Start the backend and reload.</p></div>`;
    return;
  }
  $("#scrim").onclick = closeDrawer;
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !$("#drawer").hidden) closeDrawer(); });
  window.addEventListener("hashchange", render);
  await render();
  setInterval(poll, 3000);
})();
})();
