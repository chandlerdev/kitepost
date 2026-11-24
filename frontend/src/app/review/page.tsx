"use client";
// Review & send: approve drafted emails; see what is queued, sent, missing an email, or rejected.
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { ago, fmt, SRC, short } from "@/lib/format";
import type { LeadOut, Stats, Template } from "@/lib/types";
import { useApp, useBusy, usePoll } from "@/components/AppContext";
import { ModeBanner, StPill } from "@/components/ui";
import { LeadDrawer } from "@/components/LeadDrawer";
import { TemplatePicker } from "@/components/TemplatePicker";

const TABS: { id: string; label: string; status: string; stat: keyof Stats }[] = [
  { id: "review", label: "To review", status: "review", stat: "review" },
  { id: "approved", label: "Queued", status: "approved,sending", stat: "approved" },
  { id: "sent", label: "Sent", status: "sent,replied,bounced", stat: "sent" },
  { id: "no_email", label: "No email", status: "no_email", stat: "noEmail" },
  { id: "rejected", label: "Rejected", status: "rejected,failed", stat: "rejected" }
];
const EMPTY: Record<string, [string, string?]> = {
  review: ["Nothing to review", "New customer emails appear here as campaigns find companies."],
  approved: ["The send queue is empty", "Approve emails in “To review” to queue them."],
  sent: ["No emails sent yet"], no_email: ["Every customer has an email address"], rejected: ["Nothing rejected"]
};
type Edit = { email: string; subject: string; body: string };

export default function ReviewPage() {
  const { campaigns, status, countryName, langName, toast, refresh, can } = useApp();
  const [tab, setTab] = useState("review");
  const [campaign, setCampaign] = useState("all");
  const [leads, setLeads] = useState<LeadOut[]>([]);
  const [total, setTotal] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [edits, setEdits] = useState<Record<string, Edit>>({});
  const [newEmail, setNewEmail] = useState<Record<string, string>>({});
  const [open, setOpen] = useState<string | null>(null);
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [busy, run] = useBusy();
  const t = TABS.find(x => x.id === tab)!;

  const counts = useMemo(() => {
    const list = campaign === "all" ? campaigns : campaigns.filter(c => c.id === campaign);
    return Object.fromEntries(TABS.map(x => [x.id, list.reduce((a, c) => a + (c.stats[x.stat] || 0), 0)]));
  }, [campaigns, campaign]);

  const load = useCallback(async () => {
    const r = await api.leads({ status: t.status, campaign: campaign === "all" ? undefined : campaign, category: "customer", limit: 200 });
    setLeads(r.leads); setTotal(r.total); setEdits({}); setLoaded(true);
    setSelected(sel => new Set([...sel].filter(id => r.leads.some(l => l.id === id))));
  }, [t.status, campaign]);
  useEffect(() => { api.templates().then(setTemplates).catch(() => setTemplates([])); }, []);
  useEffect(() => { setSelected(new Set()); load().catch(e => toast(e.message, true)); }, [load, toast]);
  // reload when the count changes, unless you are editing or selecting
  usePoll(() => {
    if (loaded && !Object.keys(edits).length && !selected.size && counts[tab] !== leads.length) load().catch(() => {});
  }, 3000, [counts, tab, edits, selected, leads.length, loaded]);

  const edit = (l: LeadOut): Edit => edits[l.id] || { email: l.email || "", subject: l.draft?.subject || "", body: l.draft?.body || "" };
  const setEdit = (l: LeadOut, patch: Partial<Edit>) => setEdits(e => ({ ...e, [l.id]: { ...edit(l), ...patch } }));
  const saveEdit = async (id: string) => {
    const e = edits[id]; if (!e) return;
    const hasDraft = e.subject.trim() && e.body.trim();
    await api.updateLead(id, { email: e.email.trim(), draft: hasDraft ? { subject: e.subject, body: e.body } : undefined });
    setEdits(x => { const n = { ...x }; delete n[id]; return n; });
  };
  const after = async (msg: string, bad = false) => { toast(msg, bad); await refresh(); await load(); };
  const toggle = (id: string, on: boolean) => setSelected(s => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n; });
  const m = status?.mode;

  const head = (l: LeadOut) => (
    <>
      <div className="qhead">
        <div style={{ minWidth: 0 }}>
          <h3>{l.company}</h3>
          <div className="qmeta">
            <a className="mono" href={l.url} target="_blank" rel="noopener noreferrer">{l.domain} ↗</a>
            <span>{l.location && l.location !== countryName(l.country) ? l.location + ", " : ""}{countryName(l.country)}</span>
            {l.phone && <span className="mono">{l.phone}</span>}
            <span>Fit {(Number(l.score) || 0).toFixed(1)}</span>
          </div>
        </div>
        <StPill s={l.status} />
      </div>
      <p className="sumline">{l.reason}</p>
    </>
  );

  return (
    <>
      <ModeBanner />
      <div className="head">
        <div>
          <span className="eyebrow">Review &amp; send</span><h1>Approve emails before they go out</h1>
          <p className="muted">Only customers are listed here. Nothing is sent until you approve it.{m && ` Approved emails go out one every ${m.sendIntervalSeconds}s, up to ${m.dailySendLimit} a day.`}</p>
        </div>
        <div className="headtools">
        <select aria-label="Campaign" style={{ width: "auto" }} value={campaign} onChange={e => setCampaign(e.target.value)}>
          <option value="all">All campaigns</option>
          {campaigns.map(c => <option key={c.id} value={c.id}>{short(c.name, 40)}</option>)}
        </select>
        <button className="btn" disabled={busy || !campaigns.length} title={campaign === "all" ? "Download all campaigns as an Excel sheet" : "Download this campaign as an Excel sheet"} onClick={() => run(async () => {
          const { blob, name } = await api.exportLeads(campaign);
          const url = URL.createObjectURL(blob), a = document.createElement("a");
          a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
          setTimeout(() => URL.revokeObjectURL(url), 10000);
          toast("Excel sheet downloaded: company, email and contact number.");
        })}>Export to Excel</button>
        </div>
      </div>
      <div className="seg" role="group" aria-label="Show">
        {TABS.map(x => <button key={x.id} aria-pressed={tab === x.id} onClick={() => setTab(x.id)}>{x.label} <span className="n">{fmt(counts[x.id])}</span></button>)}
      </div>

      {!can.edit && <p className="viewonly">You have view-only access. Editors and admins approve emails.</p>}
      {can.edit && tab === "review" && leads.length > 0 && (
        <div className="bulk">
          <label style={{ display: "flex", gap: 8, alignItems: "center", fontWeight: 600 }}>
            <input type="checkbox" className="check" checked={selected.size > 0 && selected.size === leads.length} onChange={e => setSelected(new Set(e.target.checked ? leads.map(l => l.id) : []))} /> Select all
          </label>
          <span className="muted">{selected.size ? `${selected.size} selected` : ""}</span>
          <button className="btn primary" style={{ marginLeft: "auto" }} disabled={!selected.size || busy} onClick={() => run(async () => {
            for (const id of selected) await saveEdit(id);
            const r = await api.approveMany([...selected]);
            setSelected(new Set());
            await after(`${r.approved} approved${r.skipped.length ? `, ${r.skipped.length} skipped: ${r.skipped[0].reason}` : ""}`, r.skipped.length > 0);
          })}>Approve selected</button>
        </div>
      )}

      <div className="queue">
        {!leads.length && loaded && <div className="empty panel"><b>{EMPTY[tab][0]}</b>{EMPTY[tab][1] && <span>{EMPTY[tab][1]}</span>}</div>}
        {leads.map(l => {
          if (tab === "review" && can.edit) {
            const e = edit(l), dirty = !!edits[l.id];
            return (
              <article key={l.id} className={`qcard${selected.has(l.id) ? " sel" : ""}`}>
                <input type="checkbox" className="check" aria-label={`Select ${l.company}`} checked={selected.has(l.id)} onChange={ev => toggle(l.id, ev.target.checked)} />
                <div className="qbody">
                  {head(l)}
                  <TemplatePicker id={`tp-${l.id}`} templates={templates && templates.filter(t => t.campaignId === l.campaignId)} value={l.templateId} disabled={busy}
                    onPick={tid => run(async () => { await saveEdit(l.id); await api.updateLead(l.id, { templateId: tid }); await load(); toast("Template applied. Check the email, then approve."); })} />
                  <div className="grid2">
                    <div className="field"><label htmlFor={`to-${l.id}`}>To</label><input type="email" id={`to-${l.id}`} value={e.email} onChange={ev => setEdit(l, { email: ev.target.value })} /></div>
                    <div className="field"><label htmlFor={`su-${l.id}`}>Subject</label><input type="text" id={`su-${l.id}`} value={e.subject} onChange={ev => setEdit(l, { subject: ev.target.value })} /></div>
                  </div>
                  {l.draft ? <>
                    <div className="field">
                      <label htmlFor={`bo-${l.id}`}>Message <span className="muted" style={{ fontWeight: 400 }}>· {langName(l.draft.language)}, from {SRC[l.draft.source] || l.draft.source}</span></label>
                      <textarea id={`bo-${l.id}`} value={e.body} onChange={ev => setEdit(l, { body: ev.target.value })} />
                    </div>
                    <div className="footer-note">Added when sending:{l.footer}</div>
                  </> : <p className="hint">Choose a template above to fill in the email. You can still edit it before approving.</p>}
                  <div className="qactions">
                    <button className="btn primary" disabled={busy || !l.draft} title={l.draft ? undefined : "Choose a template first"} onClick={() => run(async () => { await saveEdit(l.id); await api.approve(l.id); await after("Approved. It will be sent shortly."); })}>Approve &amp; send</button>
                    {dirty && <button className="btn" disabled={busy} onClick={() => run(async () => { await saveEdit(l.id); toast("Saved"); await load(); })}>Save changes</button>}
                    <button className="btn" onClick={() => setOpen(l.id)}>Details</button>
                    <button className="btn ghost" style={{ marginLeft: "auto" }} disabled={busy} onClick={() => run(async () => { await api.reject(l.id); await after("Rejected"); })}>Reject</button>
                  </div>
                </div>
              </article>
            );
          }
          return (
            <article key={l.id} className="qcard">
              <span />
              <div className="qbody">
                {head(l)}
                {tab === "review" && l.draft && <p><b>{l.draft.subject}</b> <span className="muted mono">→ {l.email}</span></p>}
                {tab === "no_email" && can.edit && (
                  <div className="kw-add">
                    <input type="email" placeholder={`Add an email address, e.g. sales@${l.domain}`} aria-label={`Email for ${l.company}`} value={newEmail[l.id] || ""} onChange={ev => setNewEmail(x => ({ ...x, [l.id]: ev.target.value }))} />
                    <button className="btn sm" disabled={busy} onClick={() => run(async () => { const v = (newEmail[l.id] || "").trim(); if (!v) throw new Error("Type an email address first."); await api.updateLead(l.id, { email: v }); await after("Saved. Choose a template in “To review”."); })}>Save address</button>
                    <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api.reject(l.id); await after("Skipped"); })}>Skip</button>
                  </div>
                )}
                {tab === "approved" && <>
                  <p><b>{l.draft?.subject}</b> <span className="muted mono">→ {l.email}</span></p>
                  <div className="qactions">{can.edit && <button className="btn" disabled={busy} onClick={() => run(async () => { await api.unapprove(l.id); await after("Moved back to review"); })}>Move back to review</button>}<button className="btn" onClick={() => setOpen(l.id)}>Details</button></div>
                </>}
                {tab === "sent" && <>
                  <p><b>{l.sent?.subject}</b> <span className="muted mono">→ {l.sent?.to}</span></p>
                  <p className="hint">{l.sent ? `${l.sent.dryRun ? "Recorded (dry run)" : "Sent"} ${ago(l.sent.at)}` : ""}{l.error ? ` · ${l.error}` : ""}</p>
                  <div className="qactions">{l.status === "replied" && <Link className="btn primary" href="/inbox">Open reply</Link>}<button className="btn" onClick={() => setOpen(l.id)}>View email</button></div>
                </>}
                {tab === "rejected" && <>
                  {l.error && <p className="err">{l.error}</p>}
                  <div className="qactions"><button className="btn" onClick={() => setOpen(l.id)}>Details</button>{can.edit && l.draft && l.email && <button className="btn" disabled={busy} onClick={() => run(async () => { await api.approve(l.id); await after("Approved. It will be sent shortly."); })}>Approve &amp; queue</button>}</div>
                </>}
              </div>
            </article>
          );
        })}
      </div>
      {total > leads.length && <p className="hint">Showing the first {leads.length} of {total}.</p>}
      {open && <LeadDrawer leadId={open} onClose={() => setOpen(null)} onChanged={() => { load(); refresh(); }} />}
    </>
  );
}
