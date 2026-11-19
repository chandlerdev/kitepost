"use client";
// Settings: connections, team (admins), audit log (admins), do-not-contact list.
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ago } from "@/lib/format";
import type { AuditEvent, Role, Suppression, TeamMember } from "@/lib/types";
import { useApp, useBusy } from "@/components/AppContext";
import { ModeBanner } from "@/components/ui";

const ROLES: { id: Role; label: string; desc: string }[] = [
  { id: "ADMIN", label: "Admin", desc: "Everything, plus team, audit log and removing people from the do-not-contact list" },
  { id: "EDITOR", label: "Editor", desc: "Create campaigns, edit and approve emails, answer replies" },
  { id: "VIEWER", label: "Viewer", desc: "See everything, change nothing" }
];
const roleLabel = (r: Role) => ROLES.find(x => x.id === r)?.label || r;

function Team() {
  const { user, toast } = useApp();
  const [list, setList] = useState<TeamMember[]>([]);
  const [form, setForm] = useState({ email: "", name: "", role: "EDITOR" as Role, password: "" });
  const [reset, setReset] = useState<{ id: string; password: string } | null>(null);
  const [busy, run] = useBusy();
  const load = useCallback(async () => setList(await api.users()), []);
  useEffect(() => { load().catch(e => toast(e.message, true)); }, [load, toast]);

  return (
    <section className="panel">
      <div><h2>Team</h2><p className="muted">New people get a temporary password and must choose their own at first sign-in. Disabling someone signs them out everywhere.</p></div>
      <div className="listbox">
        {list.map(m => (
          <div key={m.id} className="team-row">
            <div style={{ minWidth: 0 }}><b>{m.name}</b>{m.id === user?.id && <span className="muted"> (you)</span>}<div className="mono muted" style={{ overflowWrap: "anywhere" }}>{m.email}</div></div>
            <select aria-label={`Role for ${m.name}`} value={m.role} disabled={busy || m.id === user?.id} onChange={e => { const role = e.target.value as Role; run(async () => { await api.updateUser(m.id, { role }); toast(`${m.name} is now ${roleLabel(role)}`); await load(); }); }}>
              {ROLES.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}
            </select>
            <span>{!m.enabled ? <span className="pill st-rejected">Disabled</span> : m.locked ? <span className="pill st-failed">Locked</span> : m.mustChangePassword ? <span className="pill st-review">Must change password</span> : <span className="pill st-sent">Active</span>}</span>
            <div className="qactions" style={{ justifyContent: "flex-end" }}>
              <span className="hint">{m.lastLoginAt ? `Signed in ${ago(m.lastLoginAt)}` : "Never signed in"}</span>
              {m.id !== user?.id && <>
                <button className="btn sm" disabled={busy} onClick={() => setReset({ id: m.id, password: "" })}>Reset password</button>
                <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api.updateUser(m.id, { enabled: !m.enabled }); toast(m.enabled ? `${m.name} disabled and signed out` : `${m.name} enabled`); await load(); })}>{m.enabled ? "Disable" : "Enable"}</button>
              </>}
            </div>
            {reset?.id === m.id && (
              <form className="kw-add" style={{ gridColumn: "1 / -1" }} onSubmit={e => { e.preventDefault(); run(async () => { await api.resetPassword(m.id, reset.password); setReset(null); toast(`Temporary password set for ${m.name}. Share it privately.`); await load(); }); }}>
                <input type="password" autoComplete="new-password" placeholder="Temporary password (12+ characters)" aria-label="Temporary password" value={reset.password} onChange={e => setReset({ id: m.id, password: e.target.value })} />
                <button className="btn sm primary" disabled={busy || reset.password.length < 12}>Set</button>
                <button type="button" className="btn sm ghost" onClick={() => setReset(null)}>Cancel</button>
              </form>
            )}
          </div>
        ))}
      </div>
      <form onSubmit={e => { e.preventDefault(); run(async () => { await api.createUser(form); toast(`${form.name} added. Share the temporary password privately.`); setForm({ email: "", name: "", role: "EDITOR", password: "" }); await load(); }); }}
        style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <h3>Add a person</h3>
        <div className="grid2">
          <div className="field"><label htmlFor="t-name">Name</label><input type="text" id="t-name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></div>
          <div className="field"><label htmlFor="t-email">Email</label><input type="email" id="t-email" autoComplete="off" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></div>
          <div className="field"><label htmlFor="t-role">Role</label><select id="t-role" value={form.role} onChange={e => setForm({ ...form, role: e.target.value as Role })}>{ROLES.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select><span className="hint">{ROLES.find(r => r.id === form.role)?.desc}</span></div>
          <div className="field"><label htmlFor="t-pass">Temporary password</label><input type="password" id="t-pass" autoComplete="new-password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /><span className="hint">12+ characters. They will replace it at first sign-in.</span></div>
        </div>
        <div><button className="btn primary" disabled={busy || !form.email || !form.name || form.password.length < 12}>Add person</button></div>
      </form>
    </section>
  );
}

function Audit() {
  const { toast } = useApp();
  const [events, setEvents] = useState<AuditEvent[]>([]);
  useEffect(() => { api.audit(200).then(setEvents).catch(e => toast(e.message, true)); }, [toast]);
  return (
    <section className="panel">
      <div><h2>Audit log</h2><p className="muted">Sign-ins, approvals, sends, team changes and other actions, newest first.</p></div>
      <div className="tbl-wrap">
        <table style={{ minWidth: 640 }}>
          <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Details</th><th>IP</th></tr></thead>
          <tbody>
            {events.map(e => (
              <tr key={e.id} style={{ cursor: "default" }}>
                <td title={new Date(e.at).toLocaleString()}>{ago(e.at)}</td>
                <td className="mono">{e.userEmail || "—"}</td>
                <td><span className={`pill ${/failed|locked/.test(e.action) ? "st-failed" : "s-queued"}`}>{e.action}</span></td>
                <td className="muted">{[e.target, e.detail].filter(Boolean).join(" · ")}</td>
                <td className="mono muted">{e.ip}</td>
              </tr>
            ))}
            {!events.length && <tr><td colSpan={5} className="muted" style={{ textAlign: "center", padding: 24 }}>Nothing recorded yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default function SettingsPage() {
  const { status, toast, can } = useApp();
  const [sup, setSup] = useState<Suppression[]>([]);
  const [value, setValue] = useState("");
  const [busy, run] = useBusy();
  const load = useCallback(async () => setSup(await api.suppression()), []);
  useEffect(() => { load().catch(e => toast(e.message, true)); }, [load, toast]);
  const m = status?.mode;

  const rows: [string, boolean, string, string][] = m ? [
    ["Database", true, m.database, "DATABASE_URL (jdbc:postgresql://… or jdbc:mysql://…)"],
    ["Finding companies", m.search !== "demo", m.search === "demo" ? "Demo data" : m.search, "BRAVE_API_KEY, or SERPAPI_KEY, or GOOGLE_CSE_KEY + GOOGLE_CSE_CX"],
    ["Classifying & writing", m.ai === "claude", m.ai === "claude" ? `Claude · ${m.aiModel}` : "Keyword rules", "ANTHROPIC_API_KEY (and optionally ANTHROPIC_MODEL)"],
    ["Sending email", m.email === "smtp", m.email === "smtp" ? "SMTP" : "Dry run", "SMTP_HOST, SMTP_USER, SMTP_PASS and DRY_RUN=false"],
    ["Tracking replies", m.replies === "imap", { imap: "IMAP", simulated: "Simulated", off: "Off" }[m.replies], "IMAP_HOST (uses SMTP_USER/PASS unless IMAP_USER/PASS are set)"],
    ["Send pace", true, `1 per ${m.sendIntervalSeconds}s · ${m.dailySendLimit}/day`, "SEND_INTERVAL_SECONDS and DAILY_SEND_LIMIT"]
  ] : [];

  return (
    <>
      <ModeBanner />
      <div><span className="eyebrow">Settings</span><h1>Settings</h1><p className="muted">Connections are set on the server (environment variables) and restart with it. Keys and passwords are never sent to the browser.</p></div>
      <section className="panel">
        <h2>Connections</h2>
        <div className="tbl-wrap">
          <table style={{ minWidth: 560 }}>
            <thead><tr><th>Part</th><th>Now</th><th>To change, set</th></tr></thead>
            <tbody>{rows.map(([label, on, now, how]) => <tr key={label} style={{ cursor: "default" }}><td><b>{label}</b></td><td><span className={`pill ${on ? "s-sent" : "s-skipped"}`}>{now}</span></td><td className="muted">{how}</td></tr>)}</tbody>
          </table>
        </div>
      </section>
      {can.admin && <Team />}
      <section className="panel">
        <div><h2>Do-not-contact list</h2><p className="muted">Emails and domains here are never contacted. People who reply “unsubscribe” or “not interested” are added automatically.{!can.admin && " Only admins can remove entries."}</p></div>
        {can.edit && (
          <form className="kw-add" onSubmit={e => { e.preventDefault(); run(async () => { await api.suppress(value); setValue(""); toast("Added"); await load(); }); }}>
            <input type="text" value={value} onChange={e => setValue(e.target.value)} placeholder="name@company.com or company.com" aria-label="Email or domain" />
            <button className="btn" disabled={busy || !value.trim()}>Add</button>
          </form>
        )}
        <div className="listbox">
          {sup.length ? sup.map(s => (
            <div key={s.value} className="sup">
              <span><span className="mono">{s.value}</span> <span className="muted">· {s.reason} · {ago(s.at)}</span></span>
              {can.admin && <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api.unsuppress(s.value); toast("Removed"); await load(); })}>Remove</button>}
            </div>
          )) : <div className="sup muted">Nobody on the list yet.</div>}
        </div>
      </section>
      {can.admin && <Audit />}
    </>
  );
}
