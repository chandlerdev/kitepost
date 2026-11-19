"use client";
// Campaign dashboard: totals, who we found (by category), leads table (customers first), activity.
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { ago, CAT, CAT_COLOR, CAT_PLURAL, CATS, fmt, regionSummary, short } from "@/lib/format";
import type { CampaignOut, Category, LeadOut } from "@/lib/types";
import { useApp, useBusy, usePoll } from "@/components/AppContext";
import { CampaignStatus, CatPill, ModeBanner, PageError, StPill } from "@/components/ui";
import { LeadDrawer } from "@/components/LeadDrawer";

const FEED: Record<string, [string, string | null]> = {
  customer: ["★", "ok"], sent: ["✓", "ok"], approved: ["✓", "info"], reply: ["↩", "reply"], competitor: ["×", "bad"], vendor: ["◇", "warn"],
  partner: ["◆", "info"], irrelevant: ["–", null], error: ["!", "bad"], skip: ["–", "warn"], search: ["⌕", null], info: ["›", null]
};

export default function CampaignPage() {
  const { id } = useParams<{ id: string }>();
  const { countryName, toast, refresh, can } = useApp();
  const [c, setC] = useState<CampaignOut | null>(null);
  const [leads, setLeads] = useState<{ total: number; leads: LeadOut[] }>({ total: 0, leads: [] });
  const [cat, setCat] = useState<Category | "all">("customer");
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, run] = useBusy();

  const load = useCallback(async () => {
    try {
      const [cc, ll] = await Promise.all([api.campaign(id), api.leads({ campaign: id, category: cat, q })]);
      setC(cc); setLeads(ll); setError(null);
    } catch (e) { setError((e as Error).message); }
  }, [id, cat, q]);
  usePoll(load, 3000, [load]);

  if (error && !c) return <PageError message={error} />;
  if (!c) return <div className="loading">Loading campaign…</div>;
  const s = c.stats, cur = c.current;
  const totalCls = s.customer + s.partner + s.vendor + s.competitor + s.irrelevant;
  const counts: Record<string, number> = { ...s, all: s.found };
  const act = (fn: () => Promise<unknown>, msg: string) => run(async () => { await fn(); toast(msg); await load(); refresh(); });

  return (
    <>
      <ModeBanner />
      <div className="head">
        <div style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
          <span className="eyebrow">{c.goal} campaign · {c.countries.map(countryName).join(", ")}</span>
          <h1>{c.name}</h1>
          <p className="muted" style={{ maxWidth: "72ch" }}>{c.description}</p>
        </div>
        <div className="actions">
          {!can.edit ? <span className="viewonly">View only</span> : <>
          {c.status === "running" ? <button className="btn" disabled={busy} onClick={() => act(() => api.pause(c.id), "Search paused")}>Pause search</button>
            : c.status === "done" ? <button className="btn" disabled={busy} onClick={() => act(() => api.findMore(c.id, 25), "Looking for 25 more customers")}>Find 25 more customers</button>
            : <button className="btn primary" disabled={busy} onClick={() => act(() => api.start(c.id), "Search resumed")}>Resume search</button>}
          <button className="btn" disabled={busy} onClick={() => act(() => api.updateCampaign(c.id, { sending: c.sending === false }), c.sending === false ? "Sending resumed" : "Sending paused. Approved emails wait in the queue.")}>
            {c.sending === false ? "Resume sending" : "Pause sending"}
          </button></>}
          <Link className={`btn ${s.review ? "primary" : ""}`} href="/review">Review {s.review ? `(${s.review})` : ""}</Link>
        </div>
      </div>
      {c.error && <div className="banner-err">{c.error}</div>}

      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <div className="kpis five">
          <div className="kpi"><span className="eyebrow">Websites analysed</span><span className="v">{fmt(s.analysed)}</span><span className="d">{fmt(s.found - s.analysed)} in progress · limit {fmt(c.maxSites)}</span></div>
          <div className="kpi"><span className="eyebrow">Customers</span><span className="v" style={{ color: "var(--ok)" }}>{fmt(s.customer)}</span><span className="d">target {fmt(c.targetCustomers)}</span></div>
          <div className="kpi"><span className="eyebrow">Waiting for review</span><span className="v" style={{ color: s.review ? "var(--warn)" : undefined }}>{fmt(s.review)}</span><span className="d">{fmt(s.noEmail)} without an email address</span></div>
          <div className="kpi"><span className="eyebrow">Sent</span><span className="v">{fmt(s.sent)}</span><span className="d">{fmt(s.approved)} queued to send</span></div>
          <div className="kpi"><span className="eyebrow">Replies</span><span className="v" style={{ color: "var(--reply)" }}>{fmt(s.replied)}</span><span className="d">{s.sent ? ((s.replied / s.sent) * 100).toFixed(0) : 0}% reply rate{s.bounced ? ` · ${s.bounced} bounced` : ""}</span></div>
        </div>
        <div className="panel" style={{ padding: "14px 16px", gap: 10 }}>
          <div className="head"><h3>Who we found</h3><span className="sumline"><CampaignStatus c={c} />{cur && c.status === "running" && <span style={{ marginLeft: 6 }}>Now: {cur.location}, {countryName(cur.country)}</span>}</span></div>
          <div className="catbar" aria-hidden="true">{CATS.map(k => s[k] ? <span key={k} style={{ width: `${(s[k] / Math.max(1, totalCls)) * 100}%`, background: CAT_COLOR[k] }} /> : null)}</div>
          <div className="catlegend">
            {CATS.map(k => <button key={k} onClick={() => setCat(k)}><i style={{ background: CAT_COLOR[k] }} />{CAT[k]} <b>{fmt(s[k])}</b></button>)}
            {s.unreadable ? <span>Unreadable sites <b>{fmt(s.unreadable)}</b></span> : null}
          </div>
          <div className="countries">
            {c.countries.map(code => {
              const now = c.status === "running" && cur?.country === code, done = !now && c.searched.includes(code);
              return <span key={code} className={`cty ${done ? "done" : now ? "now" : ""}`}>{done ? "✓" : now ? "●" : "·"} {countryName(code)}{c.regions?.[code]?.length ? ` (${regionSummary(c.regions[code], code === "IN" ? "states/UTs" : "states")})` : ""}</span>;
            })}
          </div>
        </div>
      </div>

      <div className="split">
        <section className="panel">
          <div><h2>Leads</h2><p className="muted" style={{ fontSize: ".8rem" }}>Every website is read and compared with your campaign. Only customers get an email, and only after you approve it.</p></div>
          <div className="toolbar">
            <div className="seg" role="group" aria-label="Show category">
              {(["customer", "partner", "vendor", "competitor", "irrelevant", "all"] as const).map(k => (
                <button key={k} aria-pressed={cat === k} onClick={() => setCat(k)}>{CAT_PLURAL[k]} <span className="n">{fmt(counts[k])}</span></button>
              ))}
            </div>
            <input type="text" id="q" placeholder="Search company, domain, email" value={q} onChange={e => setQ(e.target.value)} aria-label="Search leads" style={{ marginLeft: "auto", maxWidth: 240 }} />
          </div>
          <div className="tbl-wrap">
            <table>
              <thead><tr><th>Company</th><th>Location</th><th>Fit</th><th>Category</th><th>Status</th></tr></thead>
              <tbody>
                {leads.leads.length ? leads.leads.map(l => {
                  const sc = Number(l.score) || 0, col = sc >= 8 ? "var(--ok)" : sc >= 6 ? "var(--info)" : "var(--warn)";
                  return (
                    <tr key={l.id} tabIndex={0} onClick={() => setOpen(l.id)} onKeyDown={e => { if (e.key === "Enter") setOpen(l.id); }}>
                      <td><div style={{ fontWeight: 600 }}>{l.company}</div><span className="mono muted">{l.email || l.domain}</span></td>
                      <td>{l.location && l.location !== countryName(l.country) ? l.location + ", " : ""}<span className="flag">{l.country}</span></td>
                      <td>{l.category === "customer" ? <span className="score" style={{ ["--w" as string]: `${sc * 10}%`, ["--c" as string]: col }}><i />{sc.toFixed(1)}</span> : <span className="muted">—</span>}</td>
                      <td><CatPill c={l.category} />{l.categorySource === "you" && <span className="why">set by you</span>}</td>
                      <td><StPill s={l.status} />{l.reason && l.category !== "customer" && <span className="why">{short(l.reason, 70)}</span>}</td>
                    </tr>
                  );
                }) : (
                  <tr><td colSpan={5} className="muted" style={{ textAlign: "center", padding: 30 }}>{cat === "customer" ? "No customers yet. They appear here as websites are analysed." : "Nothing in this category yet."}</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <p className="hint">{leads.total > leads.leads.length ? `Showing ${leads.leads.length} of ${leads.total}` : `${leads.total} ${leads.total === 1 ? "lead" : "leads"}`}</p>
        </section>

        <section className="panel">
          <div className="head"><h2>Activity</h2><CampaignStatus c={c} /></div>
          <ul className="feed">
            {(c.activity || []).slice(0, 40).map(a => {
              const [glyph, tone] = FEED[a.kind] || ["›", null];
              return (
                <li key={a.id}>
                  <span className="i" style={tone ? { background: `var(--${tone}-soft)`, color: `var(--${tone})` } : { background: "var(--sunk)", color: "var(--muted)" }}>{glyph}</span>
                  <div>{a.text}</div><span className="t">{ago(a.at)}</span>
                </li>
              );
            })}
            {!c.activity?.length && <li><span /><div className="muted">Nothing yet.</div><span /></li>}
          </ul>
        </section>
      </div>

      {open && <LeadDrawer leadId={open} onClose={() => setOpen(null)} onChanged={load} />}
    </>
  );
}
