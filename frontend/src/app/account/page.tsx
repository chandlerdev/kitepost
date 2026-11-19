"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { useApp } from "@/components/AppContext";

export default function AccountPage() {
  const router = useRouter();
  const { user, setUser, toast, signOut } = useApp();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (!user) return null;
  const forced = user.mustChangePassword;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (next.length < 12) return setError("Use at least 12 characters for the new password.");
    if (next !== repeat) return setError("The two new passwords don't match.");
    setBusy(true);
    try {
      const u = await api.changePassword(current, next);
      setCurrent(""); setNext(""); setRepeat("");
      setUser(u);
      toast("Password changed. Other devices were signed out.");
      if (forced) router.replace("/campaigns");
    } catch (err) { setError((err as Error).message); }
    setBusy(false);
  }

  const form = (
    <form onSubmit={submit} noValidate style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <div className="field"><label htmlFor="cur">Current password</label><input type="password" id="cur" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} /></div>
      <div className="field"><label htmlFor="new">New password</label><input type="password" id="new" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} /><span className="hint">At least 12 characters. Not a common password, and not your email address.</span></div>
      <div className="field"><label htmlFor="rep">New password again</label><input type="password" id="rep" autoComplete="new-password" value={repeat} onChange={e => setRepeat(e.target.value)} /></div>
      {error && <div className="banner-err" role="alert">{error}</div>}
      <div className="qactions"><button className="btn primary" disabled={busy || !current || !next || !repeat}>{busy ? "Saving…" : "Change password"}</button>{forced && <button type="button" className="btn ghost" onClick={signOut}>Sign out</button>}</div>
    </form>
  );

  if (forced) return (
    <div className="auth-card">
      <div><h1 style={{ fontSize: "1.35rem" }}>Choose a new password</h1><p className="muted">Hi {user.name}. Your account was set up with a temporary password. Pick your own before you continue.</p></div>
      {form}
    </div>
  );
  return (
    <>
      <div><span className="eyebrow">Account</span><h1>{user.name}</h1><p className="muted mono">{user.email} · {user.role.toLowerCase()}</p></div>
      <section className="panel" style={{ maxWidth: 520 }}><h2>Change password</h2>{form}</section>
    </>
  );
}
