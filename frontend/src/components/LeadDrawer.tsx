"use client";
// Slide-over with everything about one lead: why it got its category, the email, actions.
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { CAT, CATS, SRC, short } from "@/lib/format";
import type { Category, LeadOut } from "@/lib/types";
import { useApp, useBusy } from "./AppContext";
import { CatPill, StPill } from "./ui";
import { TemplatePicker, useTemplates } from "./TemplatePicker";

export function LeadDrawer({ leadId, onClose, onChanged }: { leadId: string; onClose: () => void; onChanged: () => void }) {
  const { toast, countryName, langName, refresh, can } = useApp();
  const [lead, setLead] = useState<LeadOut | null>(null);
  const [email, setEmail] = useState("");
  const [subject, setSubject] = useState("");
  const [bodyText, setBodyText] = useState("");
  const [busy, run] = useBusy();
  const templates = useTemplates(lead?.campaignId || "");

  const load = useCallback(async () => {
    try {
      const l = await api.lead(leadId);
      setLead(l); setEmail(l.email || ""); setSubject(l.draft?.subject || ""); setBodyText(l.draft?.body || "");
    } catch (e) { toast((e as Error).message, true); onClose(); }
  }, [leadId, toast, onClose]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  if (!lead) return null;
  const l = lead;
  const editable = can.edit && ["review", "no_email", "rejected", "failed"].includes(l.status) && l.category === "customer";
  const contacted = ["sending", "sent", "replied", "bounced"].includes(l.status);
  const patch = () => ({ email: email.trim(), draft: l.draft ? { subject, body: bodyText } : undefined });
  const done = async (msg: string) => { toast(msg); await refresh(); onChanged(); onClose(); };
  const where = `${l.location && l.location !== countryName(l.country) ? l.location + ", " : ""}${countryName(l.country)}`;

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" aria-label="Lead details">
        <header>
          <div style={{ minWidth: 0 }}>
            <span className="eyebrow">{where} · {langName(l.lang)}</span>
            <h2>{l.company}</h2>
            <a className="mono muted" href={l.url} target="_blank" rel="noopener noreferrer">{l.domain} ↗</a>
          </div>
          <button className="btn ghost" onClick={onClose} aria-label="Close" autoFocus>✕</button>
        </header>
        <div className="body">
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <CatPill c={l.category} /><StPill s={l.status} />
            {l.category === "customer" && <span className="pill s-queued">Fit {(Number(l.score) || 0).toFixed(1)} / 10</span>}
          </div>

          <section style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <h3>Why this category</h3>
            <p>{l.reason || l.error || "Still analysing this website."}</p>
            <p className="hint">Decided by {SRC[l.categorySource || ""] || "—"}{l.confidence ? ` · ${Math.round(l.confidence * 100)}% confident` : ""}{l.industry ? ` · ${l.industry}` : ""}</p>
            {can.edit && !contacted && l.status !== "analyzing" && (
              <div className="field">
                <label htmlFor="dcat">Change category</label>
                <select id="dcat" value={l.category || ""} disabled={busy} style={{ width: "auto" }}
                  onChange={e => { const v = e.target.value as Category; run(async () => { await api.updateLead(l.id, { category: v }); toast(`Marked as ${CAT[v]}`); await load(); onChanged(); refresh(); }); }}>
                  {CATS.map(k => <option key={k} value={k}>{CAT[k]}</option>)}
                </select>
              </div>
            )}
          </section>

          {l.siteSummary && <section style={{ display: "flex", flexDirection: "column", gap: 6 }}><h3>From their website</h3><p className="muted" style={{ fontSize: ".85rem" }}>{short(l.siteSummary, 420)}</p></section>}

          {l.category === "customer" && (
            <section style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <h3>{contacted ? "Email sent" : "Email for your review"}</h3>
              {contacted && l.sent ? (
                <>
                  <dl className="kv">
                    <dt>To</dt><dd className="mono">{l.sent.to}</dd>
                    <dt>Sent</dt><dd>{new Date(l.sent.at).toLocaleString()} {l.sent.dryRun && <span className="pill s-queued">Dry run</span>}</dd>
                    <dt>Subject</dt><dd>{l.sent.subject}</dd>
                  </dl>
                  <div className="preview-mail">{l.sent.text}</div>
                  {l.status === "replied" && <Link className="btn primary" href="/inbox">Open reply</Link>}
                </>
              ) : (
                <>
                  <div className="field">
                    <label htmlFor="demail">Send to</label>
                    <input type="email" id="demail" value={email} onChange={e => setEmail(e.target.value)} placeholder="name@company.com" disabled={!editable && !(can.edit && l.status === "approved")} />
                    {(l.emails?.length || 0) > 1 && <span className="hint">Also found: {l.emails!.filter(e => e !== l.email).join(", ")}</span>}
                  </div>
                  {editable && l.status !== "no_email" && l.email && (
                    <TemplatePicker id="dtpl" templates={lead.campaignId ? templates : null} value={l.templateId} disabled={busy}
                      onPick={tid => run(async () => { await api.updateLead(l.id, { email: email.trim(), templateId: tid }); toast("Template applied"); await load(); onChanged(); })} />
                  )}
                  {l.draft ? (
                    <>
                      <div className="field"><label htmlFor="dsubj">Subject</label><input type="text" id="dsubj" value={subject} onChange={e => setSubject(e.target.value)} disabled={!editable} /></div>
                      <div className="field">
                        <label htmlFor="dbody">Message <span className="muted" style={{ fontWeight: 400 }}>· {langName(l.draft.language)}, from {SRC[l.draft.source] || l.draft.source}</span></label>
                        <textarea id="dbody" style={{ minHeight: 230 }} value={bodyText} onChange={e => setBodyText(e.target.value)} disabled={!editable} />
                      </div>
                      <div className="footer-note">Added when sending:{l.footer}</div>
                    </>
                  ) : l.status === "no_email" ? <p className="muted">No email address was found on the website. Add one above, then choose a template.</p> : <p className="hint">Choose a template above to fill in the email.</p>}
                  <div className="qactions">
                    {editable && l.draft && <>
                      <button className="btn primary" disabled={busy} onClick={() => run(async () => { await api.updateLead(l.id, patch()); await api.approve(l.id); await done("Approved. It will be sent shortly."); })}>Approve &amp; send</button>
                      <button className="btn" disabled={busy} onClick={() => run(async () => { await api.updateLead(l.id, patch()); toast("Saved"); await load(); })}>Save changes</button>
                      <button className="btn ghost" disabled={busy} onClick={() => run(async () => { await api.reject(l.id); await done("Rejected. This company won't be contacted."); })}>Reject</button>
                    </>}
                    {editable && !l.draft && l.status !== "review" && <button className="btn primary" disabled={busy} onClick={() => run(async () => { await api.updateLead(l.id, { email: email.trim() }); toast("Saved"); await load(); onChanged(); })}>Save email</button>}
                    {can.edit && l.status === "approved" && <button className="btn" disabled={busy} onClick={() => run(async () => { await api.unapprove(l.id); await done("Moved back to review"); })}>Move back to review</button>}
                  </div>
                </>
              )}
            </section>
          )}
        </div>
      </aside>
    </>
  );
}
