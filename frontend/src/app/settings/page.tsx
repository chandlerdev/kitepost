"use client";
// Settings: connections, team (admins), audit log (admins), do-not-contact list.
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ago } from "@/lib/format";
import type { AuditEvent, MailInput, MailSettings, MailTest, Role, Suppression, TeamMember } from "@/lib/types";
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

const BLANK_MAIL: MailInput & { smtpPassSet: boolean; imapPassSet: boolean } = {
  smtpHost: "", smtpPort: 587, smtpSsl: false, smtpUser: "", smtpPass: "", smtpPassSet: false, fromAddress: "", sendReal: false,
  imapHost: "", imapPort: 993, imapSsl: true, imapUser: "", imapPass: "", imapPassSet: false, imapMailbox: "INBOX"
};

/** Mail configuration: the mailbox emails are sent from, and the one replies are read from. Passwords are saved encrypted. */
function MailConfig() {
  const { toast, refresh } = useApp();
  const [saved, setSaved] = useState<MailSettings | null>(null);
  const [f, setF] = useState(BLANK_MAIL);
  const [tests, setTests] = useState<{ sending?: MailTest; replies?: MailTest }>({});
  const [busy, run] = useBusy();

  const fill = useCallback((s: MailSettings) => {
    setSaved(s);
    setF({ smtpHost: s.smtpHost, smtpPort: s.smtpPort, smtpSsl: s.smtpSsl, smtpUser: s.smtpUser, smtpPass: "", smtpPassSet: s.smtpPassSet,
      fromAddress: s.fromAddress || s.effectiveFrom || "", sendReal: s.sendReal,
      imapHost: s.imapHost, imapPort: s.imapPort, imapSsl: s.imapSsl, imapUser: s.imapUser, imapPass: "", imapPassSet: s.imapPassSet, imapMailbox: s.imapMailbox || "INBOX" });
  }, []);
  useEffect(() => { api.mailSettings().then(fill).catch(e => toast(e.message, true)); }, [fill, toast]);

  const set = (patch: Partial<typeof f>) => { setF(x => ({ ...x, ...patch })); setTests({}); };
  const input = (id: string, label: string, key: "smtpHost" | "smtpUser" | "fromAddress" | "imapHost" | "imapUser" | "imapMailbox", type = "text", hint?: string, auto = "off") => (
    <div className="field"><label htmlFor={id}>{label}</label><input id={id} type={type} autoComplete={auto} value={f[key]} onChange={e => set({ [key]: e.target.value })} />{hint && <span className="hint">{hint}</span>}</div>
  );
  const pass = (id: string, label: string, key: "smtpPass" | "imapPass", isSet: boolean, hint: string) => (
    <div className="field"><label htmlFor={id}>{label}</label><input id={id} type="password" autoComplete="new-password" value={f[key]} placeholder={isSet ? "Saved (leave blank to keep it)" : ""} onChange={e => set({ [key]: e.target.value })} /><span className="hint">{hint}</span></div>
  );
  const verdict = (t?: MailTest) => t && <p className={t.ok ? "ok-note" : "err"} role="status" style={{ margin: 0 }}>{t.ok ? "✓ " : "✗ "}{t.message}</p>;

  const save = () => run(async () => {
    const { smtpPassSet, imapPassSet, ...body } = f; void smtpPassSet; void imapPassSet;
    fill(await api.saveMailSettings(body));
    await refresh();
    toast("Mail settings saved.");
  });
  const test = (kind: "sending" | "replies") => run(async () => {
    const r = await (kind === "sending" ? api.testMailSending() : api.testMailReplies());
    setTests(t => ({ ...t, [kind]: r }));
  });

  if (!saved) return <section className="panel"><h2>Mail configuration</h2><p className="muted">Loading…</p></section>;
  return (
    <section className="panel">
      <div>
        <h2>Mail configuration</h2>
        <p className="muted">The mailbox Kitepost sends from and the mailbox it reads replies in. Passwords are saved encrypted and are never shown again.
          {saved.source === "server" ? " Right now the values come from the server settings (.env); saving here replaces them." : ` Saved ${saved.updatedAt ? ago(saved.updatedAt) : ""}${saved.updatedBy ? ` by ${saved.updatedBy}` : ""}.`}</p>
      </div>
      {!saved.encryptionReady && <p className="err" role="alert">Passwords can&apos;t be saved yet: add <b>MAIL_ENCRYPTION_KEY</b> (a long random value) to the server settings, then restart the backend.</p>}
      {saved.passwordProblem && <p className="err" role="alert">A saved password can&apos;t be read (the encryption key is missing or was changed). Enter the passwords again and save.</p>}

      <h3>Sending</h3>
      <div className="grid2">
        {input("m-shost", "Sending server (SMTP)", "smtpHost", "text", "For example smtp.ionos.com")}
        <div className="field"><label htmlFor="m-sport">Port and security</label>
          <select id="m-sport" value={`${f.smtpPort}:${f.smtpSsl}`} onChange={e => { const [p, s] = e.target.value.split(":"); set({ smtpPort: Number(p), smtpSsl: s === "true" }); }}>
            <option value="587:false">587 · STARTTLS (most common)</option>
            <option value="465:true">465 · SSL/TLS</option>
            {![ "587:false", "465:true" ].includes(`${f.smtpPort}:${f.smtpSsl}`) && <option value={`${f.smtpPort}:${f.smtpSsl}`}>{f.smtpPort} · {f.smtpSsl ? "SSL/TLS" : "STARTTLS"}</option>}
          </select></div>
        {input("m-suser", "Login (username)", "smtpUser", "text", "Usually the full email address", "off")}
        {pass("m-spass", "Password", "smtpPass", f.smtpPassSet, "The mailbox password or app password")}
        {input("m-from", "Send from (From address)", "fromAddress", "email", "Every email goes out from this address")}
        <label style={{ display: "flex", gap: 8, alignItems: "center", alignSelf: "end", paddingBottom: 8 }}>
          <input type="checkbox" checked={f.sendReal} onChange={e => set({ sendReal: e.target.checked })} />
          <span><b>Send real emails</b><span className="hint" style={{ display: "block" }}>Off = approved emails are only recorded (dry run)</span></span>
        </label>
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <button type="button" className="btn sm" disabled={busy || !saved.smtpHost} onClick={() => test("sending")}>Test sending connection</button>
        {verdict(tests.sending)}
      </div>

      <h3>Replies</h3>
      <div className="grid2">
        {input("m-ihost", "Reply server (IMAP)", "imapHost", "text", "For example imap.ionos.com. Blank = replies are not read")}
        <div className="field"><label htmlFor="m-iport">Port and security</label>
          <select id="m-iport" value={`${f.imapPort}:${f.imapSsl}`} onChange={e => { const [p, s] = e.target.value.split(":"); set({ imapPort: Number(p), imapSsl: s === "true" }); }}>
            <option value="993:true">993 · SSL/TLS (most common)</option>
            <option value="143:false">143 · STARTTLS</option>
            {![ "993:true", "143:false" ].includes(`${f.imapPort}:${f.imapSsl}`) && <option value={`${f.imapPort}:${f.imapSsl}`}>{f.imapPort} · {f.imapSsl ? "SSL/TLS" : "STARTTLS"}</option>}
          </select></div>
        {input("m-iuser", "Login (username)", "imapUser", "text", "Replies come to this mailbox. Blank = the sending login")}
        {pass("m-ipass", "Password", "imapPass", f.imapPassSet, "Blank = the sending password")}
        {input("m-imbox", "Folder", "imapMailbox", "text", "Usually INBOX")}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <button type="button" className="btn sm" disabled={busy || !saved.imapHost} onClick={() => test("replies")}>Test reply connection</button>
        {verdict(tests.replies)}
      </div>
      <p className="hint" style={{ margin: 0 }}>Test buttons check the <b>saved</b> settings, so save first.</p>

      <p className="muted" style={{ margin: 0 }}>Emails go out from <b className="mono">{saved.effectiveFrom || "(not set)"}</b>; replies go to <b className="mono">{saved.effectiveReply || "(not set)"}</b>.</p>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button type="button" className="btn primary" disabled={busy} onClick={save}>Save mail settings</button>
        {saved.source === "settings" && <button type="button" className="btn ghost" disabled={busy} onClick={() => { if (confirm("Forget the saved mail settings and use the server settings (.env) again?")) run(async () => { fill(await api.resetMailSettings()); await refresh(); setTests({}); toast("Using the server settings again."); }); }}>Use server settings</button>}
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
    ["Emails are sent from", !!m.fromAddress, m.fromAddress || "Not set", "SMTP_FROM (or SMTP_USER when that is an email address)"],
    ["Tracking replies", m.replies === "imap", { imap: "IMAP", simulated: "Simulated", off: "Off" }[m.replies], "IMAP_HOST (uses SMTP_USER/PASS unless IMAP_USER/PASS are set)"],
    ["Replies are read from", m.replies === "imap" && !!m.replyAddress, m.replyAddress || "Not set", "IMAP_USER (replies go to this mailbox)"],
    ["Send pace", true, `1 per ${m.sendIntervalSeconds}s · ${m.dailySendLimit}/day · ${status?.sentToday ?? 0} sent today`, "SEND_INTERVAL_SECONDS and DAILY_SEND_LIMIT"]
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
      {can.admin && <MailConfig />}
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
