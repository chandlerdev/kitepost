/*
 * Kitepost mock server: runs entirely in the browser.
 *
 * It implements the same endpoints and returns the same JSON shapes as the Node backend
 * (server/routes.js), so the UI in app.js cannot tell the difference. Data comes from
 * /data/*.json and is kept in localStorage so a reload keeps your changes.
 *
 * Simulated here: web search (fictional .example companies), website reading,
 * classification (keyword rules), email drafting (templates), sending (recorded only)
 * and replies. When you move to Next.js, these become API route handlers.
 */
(function () {
"use strict";

const STORE_KEY = "kitepost-demo-v1";
const SEND_INTERVAL = 4;     // seconds between sends in the demo
const DAILY_LIMIT = 100;
const STEP_DELAY = 900;      // ms per simulated search/website step

let REF, TPL, NAMES, D;      // reference data, templates, mock names, database
const timers = new Map();    // campaignId -> running flag

/* ---------- helpers ---------- */
const now = () => new Date().toISOString();
const uid = (p) => p + "_" + Math.random().toString(16).slice(2, 14);
const clone = (o) => JSON.parse(JSON.stringify(o));
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const pick = (a) => a[Math.floor(Math.random() * a.length)];
const country = (code) => REF.countries.find(c => c.code === code);
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
class ApiError extends Error {}
const need = (cond, msg) => { if (!cond) throw new ApiError(msg); };
const slug = (s) => s.toLowerCase().replace(/ä/g, "ae").replace(/ö/g, "oe").replace(/ü/g, "ue").replace(/ß/g, "ss").replace(/ł/g, "l")
  .normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/&/g, "and").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

let saveTimer;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { localStorage.setItem(STORE_KEY, JSON.stringify(D)); } catch (e) { /* storage unavailable: keep in memory */ } }, 200);
}
function activity(cid, kind, text, extra) {
  D.activity.unshift(Object.assign({ id: uid("a"), campaignId: cid, kind, text, at: now() }, extra || {}));
  if (D.activity.length > 2000) D.activity.length = 2000;
  save();
}
const isSuppressed = (email) => {
  if (!email) return false;
  const e = email.toLowerCase(), dom = e.split("@")[1];
  return D.suppression.some(s => s.value === e || s.value === dom);
};
const suppress = (value, reason) => { value = value.toLowerCase(); if (!D.suppression.some(s => s.value === value)) D.suppression.push({ value, reason, at: now() }); save(); };

// shift every timestamp in the seed so the newest one is "a few minutes ago"
function shiftDates(obj) {
  const iso = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;
  let latest = 0;
  (function scan(o) { for (const k in o) { const v = o[k]; if (typeof v === "string" && iso.test(v)) latest = Math.max(latest, Date.parse(v)); else if (v && typeof v === "object") scan(v); } })(obj);
  const delta = Date.now() - 3 * 60000 - latest;
  (function walk(o) { for (const k in o) { const v = o[k]; if (typeof v === "string" && iso.test(v)) o[k] = new Date(Date.parse(v) + delta).toISOString(); else if (v && typeof v === "object") walk(v); } })(obj);
  return obj;
}

/* ---------- strategy (same rules as server/pipeline/strategy.js) ---------- */
const STOP = new Set("a an the and or of for to in on with our your we us is are be that which who what this these those from by at as it its their them they you i my new best top more most very really also into over under per via about".split(" "));
const RESELLER = /(distributor|dealer|reseller|wholesaler|retailer|integrator|stockist|agent|importer)s?\b/i;
const terms = (s) => (String(s).toLowerCase().match(/[a-zà-ÿ0-9&'-]{3,}/g) || []).filter(w => !STOP.has(w));
function offerFrom(desc, name) {
  const m = desc.match(/\bwe (?:make|sell|build|produce|offer|manufacture|provide|develop|create|roast|brew|design)\s+(?:the\s+|an?\s+|our\s+)?([^.;\n]{3,100})/i);
  return (m ? m[1] : name.split(":")[0]).trim();
}
function coreTerm(offer) {
  const parts = offer.split(", ");
  let s = parts.length > 1 && parts[0].split(" ").length <= 3 && (/\d|^[A-Z0-9-]+$/.test(parts[0]) || /^(the |an? )?[A-Z]/.test(parts[0])) ? parts.slice(1).join(", ") : parts[0];
  s = s.replace(/^(an?|the|our)\s+/i, "").replace(/\s(for|that|which|with|roasted|made|produced|based|in|to|from|who)\s.*$/i, "");
  return s.split(/\s+/).slice(0, 4).join(" ").toLowerCase();
}
function audienceFrom(desc) {
  const patterns = [
    /\b(?:looking for|searching for|want(?:ing)? to reach|want to find|targeting|target customers are|our customers are|selling to|sell to|aimed at)\s+([^.]{3,160})/i,
    /\bwe want\s+([^.]{3,160})/i, /\b(?:for)\s+([^.]{3,160})/i
  ];
  let m = null;
  for (const rx of patterns) { m = desc.match(rx); if (m) break; }
  if (!m) return [];
  return m[1].split(/,|\band\b|\bor\b|\bas well as\b/i)
    .map(p => p.replace(/\b(who|that|which|with|to|so|because|in order)\b.*$/i, "").replace(/^(the|other|more|new|all)\s+/i, "").trim())
    .filter(p => p.length > 2 && p.split(/\s+/).length <= 5 && !/\b(them|it|us|our|their|you|we)\b/i.test(p)).slice(0, 4);
}
function buildStrategy({ name, description, goal }) {
  const offer = offerFrom(description, name), core = coreTerm(offer);
  let pieces = audienceFrom(description);
  if (!pieces.length) pieces = goal === "Distributors" || goal === "Partnerships" ? ["distributors", "resellers"] : ["companies that buy " + core, "businesses using " + core];
  const ctxM = description.match(/\b(?:sell|supply|distribute|stock|carry)s?\s+(?!our\b|your\b|it\b|them\b)([a-z][a-z &-]{2,40}?)(?=\s+(?:to|for|in|across|and)\b|[.,;]|$)/i);
  const ctx = ctxM ? ctxM[1].trim().toLowerCase() : "";
  const profiles = pieces.map((p, i) => {
    const kw = RESELLER.test(p) ? [`${core} ${p}`, ctx ? `${ctx} ${p}` : `${p} ${core.split(" ").slice(-1)[0]}`] : [p];
    return { id: "p" + i, name: p.charAt(0).toUpperCase() + p.slice(1), why: `From your description: you want to reach ${p.toLowerCase()}.`, keywords: [...new Set(kw)], on: true };
  });
  return { offer, core, source: "rules", profiles, localKeywords: {}, signals: { competitor: terms(core), vendor: ["supplier to manufacturers", "components", "agency", "software for", "consulting"], partner: ["partner program", "integration", "complementary", "referral"] } };
}

/* ---------- classification (same rules as server/pipeline/classify.js) ---------- */
const RX = {
  irrelevant: /\b(business directory|company directory|find (the )?(best|top)|top \d+ |list of \d+|marketplace|compare (prices|suppliers)|job (board|openings)|we are hiring|news ?(portal|site)|trade magazine|yellow pages|listings)\b/i,
  manufacture: /\b(we (manufacture|make|produce|develop|build|design|roast)|manufacturer of|our (own )?(factory|production)|leading manufacturer|maker of)\b/i,
  vendor: /\b(we (supply|serve|help|support) (manufacturers|producers|brands|companies like|businesses like)|supplier (to|for) (manufacturers|producers|the industry)|components for|spare parts for|marketing agency|software for (manufacturers|producers)|consulting for|services for (manufacturers|producers))\b/i,
  partner: /\b(partner program(me)?|complementary|integrat(e|es|ion) with|referral (partner|program)|technology partner|alliance)\b/i,
  resell: /\b(distributor|dealer|reseller|wholesaler|retailer|stockist|importer|we (stock|carry|resell|distribute|sell))\b/i
};
const hits = (text, words) => words.reduce((n, w) => n + (w && text.includes(w.toLowerCase()) ? 1 : 0), 0);
function classify(site, c) {
  const s = c.strategy, text = `${site.title} ${site.description} ${site.text}`.toLowerCase(), titleDesc = `${site.title} ${site.description}`;
  const on = s.profiles.filter(p => p.on);
  const productWords = [...new Set([...terms(s.core || ""), ...(s.signals?.competitor || []).flatMap(terms)])];
  const profileWords = [...new Set(on.flatMap(p => [...terms(p.name), ...p.keywords.flatMap(terms)]))].filter(w => !productWords.includes(w));
  const nameHits = on.filter(p => text.includes(p.name.toLowerCase().replace(/s$/, ""))).length;
  const custHits = hits(text, profileWords) + nameHits * 2, prodHits = hits(text, productWords);
  const r = (category, confidence, reason) => ({ category, confidence, score: category === "customer" ? Math.min(10, 5 + custHits) : Math.max(1, 4 - prodHits / 2), reason, source: "rules" });
  if (RX.irrelevant.test(titleDesc)) return r("irrelevant", .8, "Looks like a directory, marketplace, job board or news site.");
  const makesProduct = RX.manufacture.test(text) && productWords.length > 0 && prodHits >= Math.min(2, productWords.length), resells = RX.resell.test(text);
  if (custHits >= 2 && !(makesProduct && !resells)) return r("customer", Math.min(.95, .55 + custHits * .08), `Matches your customer profile (${profileWords.filter(w => text.includes(w)).slice(0, 3).join(", ")}).`);
  if (makesProduct) return r("competitor", .75, `Makes a similar product (${productWords.filter(w => text.includes(w)).slice(0, 3).join(", ")}).`);
  if (RX.vendor.test(text)) return r("vendor", .7, "Sells products or services to companies like yours.");
  if (RX.partner.test(text)) return r("partner", .65, "Complementary business that could refer or integrate, not a buyer.");
  if (custHits === 1) return r("customer", .5, "Partial match with your customer profile. Check before approving.");
  return r("irrelevant", .5, "No clear link to your offer or your customer profiles.");
}

/* ---------- fictional companies (same as server/pipeline/mock.js) ---------- */
function mockSite(q, c) {
  const rr = Math.random();
  const cat = rr < .46 ? "customer" : rr < .62 ? "competitor" : rr < .74 ? "vendor" : rr < .84 ? "partner" : "irrelevant";
  const ct = country(q.country), lang = ct.lang;
  const byC = (k) => (NAMES[k] && NAMES[k][q.country]) || null;
  const sur = pick(byC("surnamesByCountry") || NAMES.surnames[lang] || NAMES.surnames.en), suf = pick(byC("suffixesByCountry") || NAMES.suffixes[lang] || NAMES.suffixes.en);
  const city = q.location && q.location !== ct.name ? q.location : pick(NAMES.cities[q.country] || [ct.name]);
  const s = c.strategy, core = s.core || "your product";
  const profile = pick(s.profiles.filter(p => p.on)), pname = profile.name.toLowerCase();
  let company = `${sur} ${suf}`, title, description, text;
  if (cat === "customer") {
    title = `${company} | ${profile.name} in ${city}`;
    description = `${company} is one of ${ct.name}'s independent ${pname}, serving customers from ${city}.`;
    text = `About us. ${company} is an established ${pname.replace(/s$/, "")} based in ${city}, ${ct.name}. ${profile.keywords[0]}: that's what our customers know us for. We have 40 staff and serve more than 600 business customers. We are always looking for new products and suppliers to add to our range, and our purchasing team reviews new offers every quarter. Contact our team for enquiries.`;
  } else if (cat === "competitor") {
    company = `${sur} ${core.split(" ").slice(-1)[0].replace(/^./, m => m.toUpperCase())} ${suf}`;
    title = `${company} | Manufacturer of ${core}`; description = `Leading manufacturer of ${core} since 1998.`;
    text = `${company} is a leading manufacturer of ${core}. We design and produce ${core} in our own factory in ${city}. Our engineers develop every model in-house and we ship to 30 countries.`;
  } else if (cat === "vendor") {
    title = `${company} | Components and services for manufacturers`; description = "We supply manufacturers with parts, tooling and engineering services.";
    text = `${company} supplies components, spare parts and engineering services to manufacturers. We help producers of ${core} shorten lead times and reduce costs. ISO 9001 certified, based in ${city}.`;
  } else if (cat === "partner") {
    title = `${company} | Software and consulting`; description = "Complementary services with a partner programme for equipment brands.";
    text = `${company} offers training, installation and maintenance planning. Our partner programme connects equipment brands with end users, and our platform integrates with leading systems. Based in ${city}.`;
  } else {
    company = `${ct.name} Business Finder`;
    title = `Top 50 ${profile.keywords[0]} in ${ct.name} | Business directory`;
    description = `Find the best ${profile.keywords[0]} near you. Compare ratings and contact details.`;
    text = `Business directory listing ${profile.keywords[0]} companies in ${ct.name}. Sponsored listings. Add your company for free.`;
  }
  const domain = slug(company.replace(/\b(GmbH|S\.?L\.?|B\.?V\.?|SARL|S\.r\.l\.|Lda\.|AB|ApS|A\/S|AS|Oy|KK|Co\.,? Ltd\.|Sp\. z o\.o\.|& Co\. KG|Pvt\. Ltd\.)(?=\s|$)/g, "")) + ".example";
  const hasEmail = cat === "irrelevant" ? Math.random() < .4 : Math.random() < .86;
  const emails = hasEmail ? [`${pick(["info", "sales", "hello", "contact"])}@${domain}`] : [];
  return { url: `https://${domain}/`, domain, title, description, text: text + (hasEmail ? ` Email: ${emails[0]}` : ""), lang, emails, pages: [`https://${domain}/`, `https://${domain}/contact`] };
}

/* ---------- drafting (same as server/pipeline/draft.js without Claude) ---------- */
function draft(c, lead) {
  const lang = c.emailLanguage === "english" ? "en" : (lead.lang || "en");
  let t = TPL.outreach[lang] || TPL.outreach.en;
  if (t.friendly) t = t[c.tone] || t.friendly;
  const s = c.sender;
  const fill = (x) => x.replace(/{company}/g, lead.company).replace(/{domain}/g, lead.domain).replace(/{sender}/g, s.name)
    .replace(/{senderCompany}/g, s.company).replace(/{role}/g, s.role ? s.role + ", " : "").replace(/{pitch}/g, c.pitch || "");
  return { subject: fill(t.subject), body: fill(t.body), language: TPL.outreach[lang] ? lang : "en", source: "template" };
}
function footerFor(c, lead) {
  const lang = lead.draft?.language || "en";
  const line = (TPL.footer[lang] || TPL.footer.en).replace(/{company}/g, lead.company);
  return `\n\n--\n${line}${c.sender.address ? `\n${c.sender.company}, ${c.sender.address}` : ""}`;
}
function prepareForReview(c, lead) {
  if (!lead.email) { lead.status = "no_email"; return; }
  if (isSuppressed(lead.email)) { lead.status = "suppressed"; return; }
  lead.draft = draft(c, lead); lead.status = "review";
}

/* ---------- campaign runner (same flow as server/pipeline/runner.js) ---------- */
function buildQueries(c) {
  const english = c.strategy.profiles.filter(p => p.on).flatMap(p => p.keywords), out = [];
  for (const code of c.countries) {
    const ct = country(code); if (!ct) continue;
    const regions = c.regions?.[code]?.length ? c.regions[code] : [ct.name];
    const local = ct.lang !== "en" && c.strategy.localKeywords?.[ct.lang]?.length ? c.strategy.localKeywords[ct.lang] : null;
    const kws = [...new Set(local ? [...local, ...english.slice(0, 2)] : english)];
    for (const loc of regions) for (const kw of kws) out.push({ q: `${kw} ${loc}${loc !== ct.name ? " " + (code === "US" ? "USA" : ct.name) : ""}`, country: code, gl: ct.gl, location: loc });
  }
  return out;
}
function stats(cid) {
  const L = D.leads.filter(l => l.campaignId === cid), by = (f) => L.filter(f).length;
  return {
    found: L.length, analysed: by(l => l.status !== "analyzing"),
    customer: by(l => l.category === "customer"), partner: by(l => l.category === "partner"), vendor: by(l => l.category === "vendor"),
    competitor: by(l => l.category === "competitor"), irrelevant: by(l => l.category === "irrelevant"), unreadable: by(l => l.status === "skipped"),
    review: by(l => l.status === "review"), noEmail: by(l => l.status === "no_email"), approved: by(l => l.status === "approved"),
    sent: by(l => ["sent", "replied", "bounced"].includes(l.status)), replied: by(l => l.status === "replied"),
    bounced: by(l => l.status === "bounced"), rejected: by(l => l.status === "rejected")
  };
}
const companyFrom = (title, domain) => { const t = (title || "").split(/\s[|–—-]\s|\s·\s/)[0].trim(); return t && t.length <= 60 ? t : domain.split(".")[0].replace(/-/g, " "); };

async function processLead(c, lead) {
  await sleep(STEP_DELAY * (0.5 + Math.random()));
  const site = lead._site; delete lead._site;
  Object.assign(lead, { title: site.title, siteSummary: site.description + " " + site.text.slice(0, 900), lang: site.lang, emails: site.emails, pages: site.pages, company: companyFrom(site.title, lead.domain) });
  const cls = classify(site, c);
  Object.assign(lead, { category: cls.category, confidence: cls.confidence, score: Math.round(cls.score * 10) / 10, reason: cls.reason, categorySource: cls.source, industry: null, email: site.emails[0] || null, updatedAt: now() });
  if (lead.category === "customer") {
    prepareForReview(c, lead);
    activity(c.id, "customer", lead.status === "review" ? `Customer found: ${lead.company.replace(/\.$/, "")}. Email drafted for your review` : `Customer found: ${lead.company}, but no email address on the site`, { leadId: lead.id });
  } else {
    lead.status = "classified";
    activity(c.id, lead.category, `Marked as ${lead.category === "irrelevant" ? "not relevant" : lead.category}: ${lead.company}`, { leadId: lead.id });
  }
  save();
}
async function run(c) {
  if (timers.get(c.id)) return;
  timers.set(c.id, true);
  try {
    while (timers.get(c.id) && c.queryIndex < c.queries.length) {
      const st = stats(c.id);
      if (st.customer >= c.targetCustomers) { activity(c.id, "info", `Reached your target of ${c.targetCustomers} customers`); break; }
      if (st.found >= c.maxSites) { activity(c.id, "info", `Reached the limit of ${c.maxSites} websites`); break; }
      const q = c.queries[c.queryIndex];
      c.current = { country: q.country, location: q.location, query: q.q };
      activity(c.id, "search", `Searching "${q.q}"`);
      await sleep(STEP_DELAY);
      const n = 4 + Math.floor(Math.random() * 4), fresh = [];
      for (let i = 0; i < n; i++) {
        const site = mockSite(q, c);
        if (D.leads.some(l => l.campaignId === c.id && l.domain === site.domain)) continue;
        const lead = { id: uid("l"), campaignId: c.id, domain: site.domain, url: site.url, company: companyFrom(site.title, site.domain), country: q.country, location: q.location, query: q.q, snippet: site.description, createdAt: now(), status: "analyzing", category: null, _site: site };
        D.leads.push(lead); fresh.push(lead);
      }
      save();
      // three at a time, like PIPELINE_CONCURRENCY=3
      for (let i = 0; i < fresh.length && timers.get(c.id); i += 3) await Promise.all(fresh.slice(i, i + 3).map(l => processLead(c, l)));
      c.queryIndex++; save();
    }
    if (timers.get(c.id)) { c.status = "done"; c.current = null; activity(c.id, "info", "Search finished. Review the drafted emails to send them."); }
  } finally { timers.delete(c.id); save(); }
}
function start(c) {
  if (timers.get(c.id)) return;
  if (!c.queries?.length) { c.queries = buildQueries(c); c.queryIndex = 0; }
  if (c.status === "done" && c.queryIndex >= c.queries.length) return;
  const first = !c.startedAt;
  c.status = "running"; c.error = null; c.startedAt = c.startedAt || now();
  activity(c.id, "info", first ? `Campaign started. Searching ${country(c.countries[0])?.name} first` : "Campaign resumed");
  // finish leads that were interrupted by a reload
  D.leads.filter(l => l.campaignId === c.id && l.status === "analyzing").forEach(l => { if (!l._site) { l.status = "skipped"; l.error = "Interrupted (page reloaded)."; } });
  run(c);
}
function pause(c) { timers.set(c.id, false); c.status = "paused"; activity(c.id, "info", "Campaign paused"); }

/* ---------- sending and replies (same as server/mail/*) ---------- */
const today = () => new Date().toISOString().slice(0, 10);
const sentToday = () => D.meta.sentByDay?.[today()] || 0;
function sendOne() {
  if (sentToday() >= DAILY_LIMIT) return;
  const l = D.leads.filter(x => x.status === "approved" && D.campaigns.find(c => c.id === x.campaignId)?.sending !== false)
    .sort((a, b) => (a.approvedAt || "").localeCompare(b.approvedAt || ""))[0];
  if (!l) return;
  const c = D.campaigns.find(x => x.id === l.campaignId);
  if (isSuppressed(l.email)) { l.status = "suppressed"; save(); return; }
  const text = l.draft.body + footerFor(c, l);
  l.sent = { at: now(), messageId: `<kp.${uid("m")}@${c.sender.email.split("@")[1]}>`, dryRun: true, to: l.email, subject: l.draft.subject, text };
  l.status = "sent";
  D.meta.sentByDay = D.meta.sentByDay || {}; D.meta.sentByDay[today()] = sentToday() + 1;
  activity(c.id, "sent", `Dry run: email recorded to ${l.company} <${l.email}>`, { leadId: l.id });
  simulateReply(l, c);
  save();
}
const INTENT_LABEL = { interested: "Interested", question: "Has questions", not_interested: "Not interested", unsubscribe: "Unsubscribe", out_of_office: "Out of office", bounce: "Bounced" };
function analyse(text, lead, c) {
  let intent = "interested";
  if (/\b(unsubscribe|remove me|stop emailing|abmelden|afmelden|baja)\b/i.test(text)) intent = "unsubscribe";
  else if (/\b(not interested|no thanks|kein interesse|pas intéressé|geen interesse)\b/i.test(text)) intent = "not_interested";
  else if (/\b(out of (the )?office|automatic reply|abwesenheit)\b/i.test(text)) intent = "out_of_office";
  const positive = /\b(interested|interesting|timing is (good|right)|send (us )?(pricing|prices|details|information|an? overview))\b|das timing passt|le moment est bien choisi|de timing is goed|el momento es oportuno/i.test(text);
  if (intent === "interested" && /\?/.test(text) && !positive) intent = "question";
  const firstLines = text.split("\n").filter(l => l.trim() && !/^(hi|hello|dear|guten|bonjour|beste|hola|hallo)\b/i.test(l)).slice(0, 2).join(" ");
  const lang = lead.draft?.language || "en";
  const answer = ["interested", "question"].includes(intent) ? (TPL.replyAnswer[lang] || TPL.replyAnswer.en).replace(/{first}/g, lead.contactFirst || "").replace(/ ,/g, ",").replace(/{sender}/g, c.sender.name).replace(/{senderCompany}/g, c.sender.company) : "";
  return { intent, summary: firstLines.slice(0, 220), draft: answer };
}
function simulateReply(lead, c) {
  const r = Math.random();
  if (r > 0.45) return;
  const intent = r < 0.22 ? "interested" : r < 0.32 ? "question" : r < 0.40 ? "not_interested" : "out_of_office";
  const lang = lead.draft?.language || "en", names = TPL.simulatedFirstNames[lang] || TPL.simulatedFirstNames.en, name = pick(names);
  const fill = (t) => t.replace(/{first}/g, c.sender.name.split(" ")[0]).replace(/{name}/g, name).replace(/{company}/g, lead.company).replace(/{country}/g, country(lead.country)?.name || "");
  const local = TPL.simulatedReplies[intent][lang], text = fill(local || TPL.simulatedReplies[intent].en);
  const translation = local && lang !== "en" ? fill(TPL.simulatedReplies[intent].en) : null;
  setTimeout(() => {
    const l = D.leads.find(x => x.id === lead.id);
    if (!l || l.status !== "sent") return;
    l.contactFirst = name;
    const a = analyse(text, l, c);
    D.replies.unshift({ id: uid("r"), leadId: l.id, campaignId: c.id, company: l.company, domain: l.domain, lang, from: l.email, fromName: `${name} (${l.company})`, subject: "Re: " + l.draft.subject, text, messageId: `<sim.${uid("m")}@${l.domain}>`, receivedAt: now(), intent: a.intent, summary: a.summary, translation, draft: a.draft, analysisSource: "rules", unread: true, handled: false, simulated: true });
    if (a.intent !== "out_of_office") l.status = "replied";
    if (a.intent === "not_interested" || a.intent === "unsubscribe") suppress(l.email, a.intent);
    activity(c.id, "reply", `${INTENT_LABEL[a.intent]}: reply from ${name} (${l.company})`, { leadId: l.id });
    save();
  }, 10000 + Math.random() * 30000);
}

/* ---------- API (mirrors server/routes.js) ---------- */
const getC = (id) => { const c = D.campaigns.find(x => x.id === id); need(c, "Campaign not found"); return c; };
const getL = (id) => { const l = D.leads.find(x => x.id === id); need(l, "Lead not found"); return l; };
const campaignOut = (c) => Object.assign(clone(c), { queries: undefined, queryCount: c.queries?.length || 0, searched: [...new Set((c.queries || []).slice(0, c.queryIndex).map(q => q.country))], stats: stats(c.id) });
const leadOut = (l) => { const c = D.campaigns.find(x => x.id === l.campaignId); const o = clone(Object.assign({}, l, { _site: undefined })); o.footer = l.draft && c ? footerFor(c, l) : null; return o; };
function approveLead(l) {
  need(l.category === "customer", `${l.company} is not marked as a customer.`);
  need(l.email && l.draft, `${l.company} has no email address or draft.`);
  need(["review", "rejected", "failed"].includes(l.status), `${l.company} can't be approved in its current state (${l.status}).`);
  need(!isSuppressed(l.email), `${l.email} is on your do-not-contact list.`);
  l.status = "approved"; l.approvedAt = now(); l.updatedAt = now(); l.error = null;
  activity(l.campaignId, "approved", `Approved: ${l.company}`, { leadId: l.id });
}

const api = {
  status: () => ({ mode: { search: "demo", ai: "rules", aiModel: null, email: "dry-run", replies: "simulated", dailySendLimit: DAILY_LIMIT, sendIntervalSeconds: SEND_INTERVAL, browserOnly: true }, sentToday: sentToday(), reviewCount: D.leads.filter(l => l.status === "review").length, unreadReplies: D.replies.filter(r => r.unread).length }),
  reference: () => clone(REF),
  strategy: (b) => {
    need((b.name || "").trim(), "Add a campaign name.");
    need((b.description || "").trim().length >= 30, "Describe your offer in at least 30 characters.");
    return buildStrategy(b);
  },
  campaigns: () => D.campaigns.map(campaignOut),
  campaign: (id) => Object.assign(campaignOut(getC(id)), { activity: clone(D.activity.filter(a => a.campaignId === id).slice(0, 60)) }),
  createCampaign: (b) => {
    need(b.name?.trim(), "Add a campaign name.");
    need(b.description?.trim().length >= 30, "Describe your offer in at least 30 characters.");
    need(Array.isArray(b.countries) && b.countries.length && b.countries.every(country), "Pick at least one country.");
    need(b.strategy?.profiles?.some(p => p.on), "Keep at least one customer profile switched on.");
    const s = b.sender || {};
    need(s.name?.trim() && s.company?.trim(), "Add the sender's name and company.");
    need(EMAIL.test(s.email || ""), "Add a valid sender email address.");
    need(!b.countries.includes("US") || s.address?.trim(), "Add your postal address. US law (CAN-SPAM) requires it in commercial emails.");
    const c = {
      id: uid("c"), name: b.name.trim(), description: b.description.trim(), goal: b.goal || "Sales", countries: b.countries, regions: b.regions || {},
      strategy: clone(b.strategy), pitch: b.description.trim().split(/(?<=[.!?])\s+/).slice(0, 2).join(" "),
      sender: { name: s.name.trim(), company: s.company.trim(), email: s.email.trim(), website: s.website || "", role: s.role || "", phone: s.phone || "", address: s.address || "" },
      tone: b.tone || "friendly", emailLanguage: b.emailLanguage === "english" ? "english" : "local",
      targetCustomers: Math.max(1, Math.min(1000, Number(b.targetCustomers) || 25)), maxSites: Math.max(10, Math.min(5000, Number(b.maxSites) || 250)),
      status: "draft", queries: [], queryIndex: 0, createdAt: now(), sending: true
    };
    D.campaigns.unshift(c); activity(c.id, "info", "Campaign created");
    if (b.start !== false) start(c);
    return campaignOut(c);
  },
  updateCampaign: (id, b) => {
    const c = getC(id);
    if (b.name) c.name = String(b.name).trim();
    if (b.tone) c.tone = b.tone;
    if (typeof b.sending === "boolean") { c.sending = b.sending; activity(c.id, "info", b.sending ? "Sending resumed" : "Sending paused"); }
    if (b.targetCustomers) c.targetCustomers = Number(b.targetCustomers);
    if (b.sender) Object.assign(c.sender, b.sender);
    save(); return campaignOut(c);
  },
  start: (id) => { const c = getC(id); start(c); return campaignOut(c); },
  pause: (id) => { const c = getC(id); pause(c); return campaignOut(c); },
  findMore: (id, add) => {
    const c = getC(id);
    c.targetCustomers += Number(add) || 25;
    if (c.queryIndex >= c.queries.length) { c.queries = c.queries.concat(buildQueries(c)); } // demo: search the same places again for new companies
    if (c.status === "done") c.status = "paused";
    start(c); return campaignOut(c);
  },
  leads: (f) => {
    f = f || {};
    let list = f.campaign ? D.leads.filter(l => l.campaignId === f.campaign) : D.leads;
    if (f.category && f.category !== "all") list = list.filter(l => l.category === f.category);
    if (f.status && f.status !== "all") list = list.filter(l => f.status.split(",").includes(l.status));
    if (f.q) { const s = String(f.q).toLowerCase(); list = list.filter(l => [l.company, l.domain, l.email, l.reason].some(v => v && v.toLowerCase().includes(s))); }
    list = [...list].sort((a, b) => (b.updatedAt || b.createdAt).localeCompare(a.updatedAt || a.createdAt));
    const limit = Math.min(500, Number(f.limit) || 200);
    return { total: list.length, leads: list.slice(0, limit).map(leadOut) };
  },
  lead: (id) => leadOut(getL(id)),
  updateLead: (id, b) => {
    const l = getL(id), c = getC(l.campaignId);
    need(!["sending", "sent", "replied"].includes(l.status) || !b.draft, "This email has already been sent.");
    if (b.email !== undefined) {
      need(b.email === "" || EMAIL.test(b.email), "That email address doesn't look right.");
      l.email = b.email || null;
      if (l.category === "customer" && ["no_email", "review"].includes(l.status)) prepareForReview(c, l);
    }
    if (b.draft) {
      need(b.draft.subject?.trim() && b.draft.body?.trim(), "The email needs a subject and a body.");
      l.draft = Object.assign({}, l.draft, { subject: b.draft.subject.trim(), body: b.draft.body, source: "you" });
    }
    if (b.category && b.category !== l.category) {
      need(REF.categories.some(x => x.id === b.category), "Unknown category.");
      need(!["sending", "sent", "replied", "bounced"].includes(l.status), "This company has already been contacted.");
      l.category = b.category; l.categorySource = "you"; l.reason = `Marked as ${b.category} by you.`;
      if (b.category === "customer") prepareForReview(c, l); else l.status = "classified";
      activity(c.id, "info", `You marked ${l.company} as ${b.category}`, { leadId: l.id });
    }
    l.updatedAt = now(); save(); return leadOut(l);
  },
  approve: (id) => { const l = getL(id); approveLead(l); save(); return leadOut(l); },
  approveMany: (ids) => {
    const done = [], skipped = [];
    for (const id of ids || []) { const l = D.leads.find(x => x.id === id); if (!l) continue; try { approveLead(l); done.push(id); } catch (e) { skipped.push({ id, reason: e.message }); } }
    save(); return { approved: done.length, skipped };
  },
  reject: (id) => { const l = getL(id); need(["review", "approved", "no_email", "failed"].includes(l.status), "This email can't be rejected now."); l.status = "rejected"; l.updatedAt = now(); activity(l.campaignId, "info", `Rejected: ${l.company}`, { leadId: l.id }); return leadOut(l); },
  unapprove: (id) => { const l = getL(id); need(l.status === "approved", "Only queued emails can be moved back to review."); l.status = "review"; l.updatedAt = now(); save(); return leadOut(l); },
  redraft: (id) => { const l = getL(id); need(["review", "rejected", "no_email", "failed"].includes(l.status), "This email can't be rewritten now."); l.draft = draft(getC(l.campaignId), l); l.updatedAt = now(); save(); return leadOut(l); },
  replies: () => D.replies.map(r => { const l = D.leads.find(x => x.id === r.leadId); return Object.assign(clone(r), { campaignName: D.campaigns.find(c => c.id === r.campaignId)?.name, leadEmail: l?.email, sentEmail: l?.sent ? clone(l.sent) : null }); }),
  updateReply: (id, b) => {
    const r = D.replies.find(x => x.id === id); need(r, "Reply not found");
    if (typeof b.handled === "boolean") r.handled = b.handled;
    if (typeof b.unread === "boolean") r.unread = b.unread;
    if (typeof b.draft === "string") r.draft = b.draft;
    save(); return clone(r);
  },
  answer: (id, body) => {
    const r = D.replies.find(x => x.id === id); need(r, "Reply not found");
    body = String(body || "").trim(); need(body, "Write an answer first.");
    r.answers = r.answers || []; r.answers.push({ at: now(), body, messageId: `<kp.${uid("m")}>`, dryRun: true }); r.handled = true;
    activity(r.campaignId, "sent", `Dry run: answer recorded to ${r.fromName}`, { leadId: r.leadId });
    save(); return Object.assign(clone(r), { dryRun: true });
  },
  checkInbox: () => ({ ok: true, imap: false }),
  suppression: () => clone(D.suppression),
  suppress: (v) => { v = String(v || "").trim(); need(v, "Add an email address or domain."); suppress(v, "added by you"); return clone(D.suppression); },
  unsuppress: (v) => { D.suppression = D.suppression.filter(s => s.value !== String(v).toLowerCase()); save(); return clone(D.suppression); }
};

/* ---------- boot ---------- */
async function loadJSON(f) {
  const r = await fetch("data/" + f, { cache: "no-store" });
  if (!r.ok) throw new Error(`data/${f} returned HTTP ${r.status}`);
  return r.json();
}
let ready = null;
function init() {
  if (ready) return ready;
  ready = (async () => {
    const [ref, tpl, names] = await Promise.all([loadJSON("reference.json"), loadJSON("templates.json"), loadJSON("mock-companies.json")]);
    REF = ref; TPL = tpl; NAMES = names;
    let stored = null;
    try { stored = JSON.parse(localStorage.getItem(STORE_KEY) || "null"); } catch (e) { stored = null; }
    D = stored || shiftDates(await loadJSON("seed.json"));
    D.meta = D.meta || {}; D.suppression = D.suppression || [];
    // campaigns that were searching when the page closed carry on
    D.campaigns.filter(c => c.status === "running").forEach(c => { c.status = "paused"; start(c); });
    setInterval(sendOne, SEND_INTERVAL * 1000);
    save();
  })();
  return ready;
}

window.MockServer = {
  init,
  // every call is async with a small delay, like a real network request
  async call(name, ...args) {
    await init();
    await sleep(60 + Math.random() * 120);
    if (!api[name]) throw new Error(`Unknown API call ${name}`);
    return api[name](...args);
  },
  async reset(empty) {
    for (const k of timers.keys()) timers.set(k, false);
    try { localStorage.removeItem(STORE_KEY); } catch (e) {}
    D = empty ? { campaigns: [], leads: [], replies: [], activity: [], suppression: [], meta: {} } : shiftDates(await loadJSON("seed.json"));
    D.meta = D.meta || {}; save();
  },
  exportData: () => clone(D)
};
})();
