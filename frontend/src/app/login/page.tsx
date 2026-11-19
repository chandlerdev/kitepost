"use client";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useApp } from "@/components/AppContext";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { user, setUser } = useApp();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // only allow redirects within this app
  const next = (() => { const n = params.get("next") || "/campaigns"; return n.startsWith("/") && !n.startsWith("//") && n !== "/login" ? n : "/campaigns"; })();

  useEffect(() => { if (user) router.replace(user.mustChangePassword ? "/account" : next); }, [user, next, router]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const u = await api.login(email.trim(), password);
      setPassword("");
      setUser(u);
      router.replace(u.mustChangePassword ? "/account" : next);
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="auth-card">
      <div className="brand">
        <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true"><rect width="26" height="26" rx="7" fill="var(--accent)" /><path d="M7 18 L13 6 L19 18 L13 14.5 Z" fill="var(--accent-ink)" /></svg>
        Kitepost
      </div>
      <div><h1 style={{ fontSize: "1.35rem" }}>Sign in</h1><p className="muted">Use the account your admin created for you.</p></div>
      <form onSubmit={submit} noValidate>
        <div className="field"><label htmlFor="email">Email</label><input type="email" id="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required autoFocus /></div>
        <div className="field"><label htmlFor="password">Password</label><input type="password" id="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></div>
        {error && <div className="banner-err" role="alert">{error}</div>}
        <button className="btn primary" disabled={busy || !email || !password}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>
      <p className="hint">After 5 wrong passwords the account is locked for 15 minutes. Forgot your password? Ask an admin to reset it.</p>
    </div>
  );
}

export default function LoginPage() {
  return <Suspense fallback={<div className="loading">Loading…</div>}><LoginForm /></Suspense>;
}
