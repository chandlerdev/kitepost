"use client";
// New campaign: offer → where → customers (AI strategy) → sender → review & start.
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { fmt, regionSummary } from "@/lib/format";
import type { NewCampaignInput, Sender, Strategy } from "@/lib/types";
import { useApp } from "@/components/AppContext";
import { ModeBanner } from "@/components/ui";

const STEPS = ["Your offer", "Where", "Customers", "Sender", "Review & start"];
type Form = Omit<NewCampaignInput, "strategy"> & { strategy: Strategy | null; regions: Record<string, string[]> };
const EMPTY: Form = {
  name: "", description: "", goal: "Sales", countries: ["US", "IN"], regions: {}, strategy: null,
  sender: { name: "", company: "", email: "", website: "", role: "", phone: "", address: "" },
  tone: "friendly", emailLanguage: "local", targetCustomers: 25, maxSites: 250
};
const EXAMPLE = {
  name: "Dental scheduling software: US and India clinics", goal: "Sales",
  description: "We build ChairTime, online scheduling and reminder software for dental clinics. We are looking for independent dental practices, orthodontists and small dental groups that still book appointments by phone. We do not want dental suppliers, labs or other software companies."
};
const SENDER_FIELDS: [keyof Sender, string, string, boolean][] = [
  ["name", "Full name", "text", true], ["company", "Company", "text", true],
  ["role", "Position or role", "text", false], ["website", "Website", "url", false], ["phone", "Phone", "tel", false]
];

export default function NewCampaignPage() {
  const router = useRouter();
  const { ref, status, toast, refresh, countryName, langName, can } = useApp();
  const [step, setStep] = useState(0);
  const [f, setF] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [builtFor, setBuiltFor] = useState("");
  const [newKw, setNewKw] = useState<Record<string, string>>({});
  const [newProfile, setNewProfile] = useState("");
  const [submitting, setSubmitting] = useState(false);
  if (!can.edit) return <div className="load-error"><h2>View-only access</h2><p className="muted">Your role can look at campaigns but not create them. Ask an admin for the editor role.</p></div>;
  if (!ref || !status) return <div className="loading">Loading…</div>;

  const set = (patch: Partial<Form>) => setF(x => ({ ...x, ...patch }));
  const setSender = (k: keyof Sender, v: string) => setF(x => ({ ...x, sender: { ...x.sender, [k]: v } }));
  const setStrategy = (fn: (s: Strategy) => Strategy) => setF(x => (x.strategy ? { ...x, strategy: fn(structuredClone(x.strategy)) } : x));
  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter(x => x !== v) : [...list, v]);
  const usOn = f.countries.includes("US");

  async function buildStrategy(force: boolean) {
    const key = JSON.stringify([f.name, f.description, f.goal, f.countries]);
    if (!force && f.strategy && builtFor === key) return;
    setLoading(true); setErrors({});
    try { const s = await api.strategy({ name: f.name, description: f.description, goal: f.goal, countries: f.countries }); set({ strategy: s }); setBuiltFor(key); }
    catch (e) { setErrors({ strategy: (e as Error).message }); set({ strategy: null }); }
    setLoading(false);
  }

  async function next() {
    const er: Record<string, string> = {};
    if (step === 0) { if (!f.name.trim()) er.name = "Give the campaign a name."; if (f.description.trim().length < 30) er.description = "Describe your offer and customers in at least 30 characters."; }
    if (step === 1 && !f.countries.length) er.countries = "Pick at least one country.";
    if (step === 2) { if (!f.strategy) er.strategy = "Rebuild the customer profiles first."; else if (!f.strategy.profiles.some(p => p.on && p.keywords.length)) er.profiles = "Keep at least one customer type with a search phrase."; }
    if (step === 3) {
      if (!f.sender.name.trim()) er.name = "Add the name that signs your emails.";
      if (!f.sender.company.trim()) er.company = "Add your company name.";
      if (usOn && !f.sender.address.trim()) er.address = "Add your postal address. It's required for emails to US companies.";
    }
    setErrors(er);
    if (Object.keys(er).length) return;
    if (step === 1) { setStep(2); return buildStrategy(false); }
    if (step < 4) return setStep(step + 1);
    setSubmitting(true);
    try {
      const created = await api.createCampaign({ ...f, strategy: f.strategy!, regions: Object.fromEntries(Object.entries(f.regions).filter(([k, v]) => f.countries.includes(k) && v.length)), start: true });
      toast("Campaign started. Customers appear as websites are analysed.");
      await refresh();
      router.push(`/campaigns/${created.id}`);
    } catch (e) { setErrors({ submit: (e as Error).message }); setSubmitting(false); }
  }

  const groups = [...new Set(ref.countries.map(c => c.region))];
  const st = f.strategy;

  return (
    <>
      <ModeBanner />
      <div className="wiz">
        <div><span className="eyebrow">New campaign</span><h1>Set up your outreach</h1></div>
        <ol className="steps">{STEPS.map((n, i) => <li key={n} className={i < step ? "done" : i === step ? "cur" : ""}>{n}</li>)}</ol>
        <section className="panel">
          {step === 0 && <>
            <div className="head">
              <div><h2>Say what you sell, and to whom</h2><p className="muted">The campaign name and description decide which companies count as customers. Partners, vendors and competitors are marked and left out.</p></div>
              <button className="btn ghost sm" onClick={() => set(EXAMPLE)}>Fill with an example</button>
            </div>
            <div className="field"><label htmlFor="w-name">Campaign name</label><input type="text" id="w-name" value={f.name} onChange={e => set({ name: e.target.value })} placeholder="e.g. Dental scheduling software: US clinics" />{errors.name && <span className="err">{errors.name}</span>}</div>
            <div className="field"><span className="lbl">Goal</span><div className="chips">{ref.goals.map(g => <button key={g} type="button" className="chip" aria-pressed={f.goal === g} onClick={() => set({ goal: g })}>{g}</button>)}</div></div>
            <div className="field">
              <label htmlFor="w-desc">Describe your offer and your customers</label>
              <textarea id="w-desc" maxLength={1500} value={f.description} onChange={e => set({ description: e.target.value })} placeholder="We make … We want to reach … because …" />
              <span className="hint">{f.description.length} / 1,500. Say what you sell, who buys it, and anyone you don&apos;t want to reach.</span>
              {errors.description && <span className="err">{errors.description}</span>}
            </div>
          </>}

          {step === 1 && <>
            <div><h2>Where should we look?</h2><p className="muted">Countries are searched in the order you pick them. Emails are written in each company&apos;s language unless you choose English below.</p></div>
            {groups.map(g => (
              <div key={g} className="region-group"><span className="eyebrow">{g}</span>
                <div className="chips">{ref.countries.filter(c => c.region === g).map(c => (
                  <button key={c.code} type="button" className="chip" aria-pressed={f.countries.includes(c.code)} onClick={() => set({ countries: toggle(f.countries, c.code) })}>
                    {c.name}{f.countries.includes(c.code) && f.countries.length > 1 ? <span className="n"> {f.countries.indexOf(c.code) + 1}</span> : null}
                  </button>
                ))}</div>
              </div>
            ))}
            {errors.countries && <span className="err">{errors.countries}</span>}
            {f.countries.filter(code => ref.regions[code]).map(code => {
              const reg = ref.regions[code], chosen = f.regions[code] || [];
              const setChosen = (list: string[]) => set({ regions: { ...f.regions, [code]: list } });
              return (
                <div key={code} className="states">
                  <div className="head">
                    <div><b>{countryName(code)}: which {reg.label}?</b><p className="hint">{chosen.length ? `${chosen.length} selected. Each one is searched separately.` : `All ${reg.label}. Pick some to search one by one and get more local results.`}</p></div>
                    {chosen.length > 0 && <button className="btn sm ghost" onClick={() => setChosen([])}>Use all {reg.label}</button>}
                  </div>
                  <div className="chips">{reg.list.map(s => <button key={s} type="button" className="chip" aria-pressed={chosen.includes(s)} onClick={() => setChosen(toggle(chosen, s))}>{s}</button>)}</div>
                </div>
              );
            })}
            <div className="field"><span className="lbl">Email language</span>
              <div className="choice-row">
                <button type="button" className="choice" aria-pressed={f.emailLanguage === "local"} onClick={() => set({ emailLanguage: "local" })}><b>Each company&apos;s language</b><span>German for German sites, French for French sites.</span></button>
                <button type="button" className="choice" aria-pressed={f.emailLanguage === "english"} onClick={() => set({ emailLanguage: "english" })}><b>Always English</b><span>One language for every email.</span></button>
              </div>
            </div>
            <div className="numrow">
              <div className="field"><label htmlFor="w-target">Stop after this many customers</label><input type="number" id="w-target" min={1} max={1000} value={f.targetCustomers} onChange={e => set({ targetCustomers: Number(e.target.value) })} /></div>
              <div className="field"><label htmlFor="w-max">Analyse at most this many websites</label><input type="number" id="w-max" min={10} max={5000} value={f.maxSites} onChange={e => set({ maxSites: Number(e.target.value) })} /><span className="hint">Limits search and AI costs.</span></div>
            </div>
          </>}

          {step === 2 && <>
            <div className="head">
              <div><h2>Who counts as a customer</h2><p className="muted">Built from your campaign name and description. Websites matching these profiles become customers. Others are marked as partner, vendor, competitor or not relevant.</p></div>
              {st && !loading && <button className="btn sm" onClick={() => buildStrategy(true)}>Rebuild</button>}
            </div>
            {loading ? <div className="gen"><b>Working out your customers</b><ul><li className="ok"><span className="ic"><span className="spin" /></span>Reading your description and building search keywords…</li></ul></div>
              : errors.strategy ? <div className="banner-err">{errors.strategy}</div>
              : st && <>
                <p className="sumline">You sell: <b style={{ color: "var(--ink)" }}>{st.offer}</b> · built with {st.source === "claude" ? "Claude" : "keyword rules (add ANTHROPIC_API_KEY for better results)"}</p>
                <div className="personas">
                  {st.profiles.map((p, pi) => (
                    <div key={p.id} className={`persona${p.on ? "" : " off"}`}>
                      <header>
                        <div><h3>{p.name}</h3><p className="muted" style={{ fontSize: ".8rem" }}>{p.why}</p></div>
                        <input type="checkbox" className="switch" aria-label={`Use ${p.name}`} checked={p.on} onChange={e => setStrategy(s => { s.profiles[pi].on = e.target.checked; return s; })} />
                      </header>
                      <div className="chips">{p.keywords.map((k, ki) => (
                        <span key={k + ki} className="chip kw">{k}<button type="button" aria-label={`Remove ${k}`} onClick={() => setStrategy(s => { s.profiles[pi].keywords.splice(ki, 1); return s; })}>×</button></span>
                      ))}</div>
                      <form className="kw-add" onSubmit={e => { e.preventDefault(); const v = (newKw[p.id] || "").trim(); if (!v) return; setStrategy(s => { s.profiles[pi].keywords.push(v); return s; }); setNewKw(x => ({ ...x, [p.id]: "" })); }}>
                        <input type="text" placeholder="Add a search phrase" aria-label={`Add keyword to ${p.name}`} value={newKw[p.id] || ""} onChange={e => setNewKw(x => ({ ...x, [p.id]: e.target.value }))} />
                        <button className="btn sm">Add</button>
                      </form>
                    </div>
                  ))}
                  <div className="persona"><h3>Add a customer type</h3>
                    <form className="kw-add" onSubmit={e => { e.preventDefault(); const v = newProfile.trim(); if (!v) return; setStrategy(s => { s.profiles.push({ id: "p" + Date.now(), name: v, why: "Added by you.", keywords: [v.toLowerCase()], on: true }); return s; }); setNewProfile(""); }}>
                      <input type="text" placeholder="e.g. Hospital procurement teams" aria-label="New customer type" value={newProfile} onChange={e => setNewProfile(e.target.value)} />
                      <button className="btn sm">Add</button>
                    </form>
                  </div>
                </div>
                {Object.keys(st.localKeywords || {}).length > 0 && <p className="hint">Also searching in local languages: {Object.entries(st.localKeywords).map(([l, k]) => `${langName(l)} (${k.length} phrases)`).join(", ")}.</p>}
                {errors.profiles && <span className="err">{errors.profiles}</span>}
              </>}
          </>}

          {step === 3 && <>
            <div><h2>Who is sending?</h2><p className="muted">Used to sign each email.{status.mode.fromAddress ? <> Emails are sent from <b className="mono">{status.mode.fromAddress}</b>{status.mode.replyAddress && status.mode.replyAddress !== status.mode.fromAddress ? <> and replies go to <b className="mono">{status.mode.replyAddress}</b></> : <>, and replies come back to the same mailbox</>}. These are set on the server (Settings).</> : <> The sending and reply mailboxes are set on the server (Settings); none is set yet, so emails are only recorded.</>}</p></div>
            <div className="grid2">{SENDER_FIELDS.map(([k, label, type, req]) => (
              <div key={k} className="field"><label htmlFor={`s-${k}`}>{label}{req ? " *" : ""}</label><input type={type} id={`s-${k}`} value={f.sender[k]} onChange={e => setSender(k, e.target.value)} />{errors[k] && <span className="err">{errors[k]}</span>}</div>
            ))}</div>
            <div className="field">
              <label htmlFor="s-address">Postal address{usOn ? " *" : ""}</label>
              <input type="text" id="s-address" value={f.sender.address} onChange={e => setSender("address", e.target.value)} placeholder="Street, city, postcode, country" />
              <span className="hint">{usOn ? "Required for US recipients by the CAN-SPAM Act. It's added to the footer of every email." : "Recommended. Added to the footer of every email with an unsubscribe line."}</span>
              {errors.address && <span className="err">{errors.address}</span>}
            </div>
            <div className="field"><span className="lbl">How should your emails sound?</span>
              <div className="choice-row">{ref.tones.map(t => <button key={t.id} type="button" className="choice" aria-pressed={f.tone === t.id} onClick={() => set({ tone: t.id })}><b>{t.label}</b><span>{t.desc}</span></button>)}</div>
            </div>
          </>}

          {step === 4 && <>
            <div><h2>Ready to start</h2><p className="muted">Starting only searches and drafts. Every email waits for your approval in Review &amp; send.</p></div>
            {errors.submit && <div className="banner-err">{errors.submit}</div>}
            <dl className="review">
              <dt>Campaign</dt><dd><b>{f.name}</b> · {f.goal}</dd>
              <dt>You sell</dt><dd>{st?.offer}</dd>
              <dt>Customers</dt><dd>{st?.profiles.filter(p => p.on).map(p => p.name).join(", ")}</dd>
              <dt>Countries</dt><dd>{f.countries.map(c => countryName(c) + (f.regions[c]?.length ? ` (${regionSummary(f.regions[c], ref.regions[c]?.label || "regions")})` : "")).join(" → ")}</dd>
              <dt>Limits</dt><dd>Stop at {fmt(f.targetCustomers)} customers or {fmt(f.maxSites)} websites</dd>
              <dt>Emails</dt><dd>{f.emailLanguage === "local" ? "In each company's language" : "In English"} · {f.tone} tone</dd>
              <dt>Sender</dt><dd>{f.sender.name}, {f.sender.company}{status.mode.fromAddress && <> · <span className="mono">{status.mode.fromAddress}</span></>}</dd>
              <dt>Sending</dt><dd>{status.mode.email === "smtp" ? `Live SMTP, one email every ${status.mode.sendIntervalSeconds}s after approval` : "Dry run: approved emails are recorded, not sent"}</dd>
            </dl>
          </>}
        </section>
        <div className="wiz-foot">
          <button className="btn" disabled={step === 0} onClick={() => { setErrors({}); setStep(step - 1); }}>Back</button>
          <button className="btn primary" disabled={loading || submitting} onClick={next}>{step === 4 ? (submitting ? "Starting…" : "Start campaign") : "Continue"}</button>
        </div>
      </div>
    </>
  );
}
