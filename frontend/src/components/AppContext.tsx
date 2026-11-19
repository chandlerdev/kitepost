"use client";
// App-wide state: the signed-in user, reference data, server status, campaign list, toasts.
import { usePathname, useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api, BASE, PASSWORD_REQUIRED, SIGNED_OUT } from "@/lib/api";
import type { CampaignOut, Reference, Status, User } from "@/lib/types";

const PUBLIC = ["/login"];
/** The browser address without the base path, to compare with routes such as /login. */
const here = () => (BASE && window.location.pathname.startsWith(BASE) ? window.location.pathname.slice(BASE.length) : window.location.pathname) || "/";

interface Ctx {
  user: User | null;
  can: { edit: boolean; admin: boolean };
  ref: Reference | null;
  status: Status | null;
  campaigns: CampaignOut[];
  error: string | null;
  refresh: () => Promise<void>;
  setUser: (u: User | null) => void;
  signOut: () => Promise<void>;
  toast: (msg: string, bad?: boolean) => void;
  countryName: (code: string) => string;
  langName: (lang?: string) => string;
}
const AppCtx = createContext<Ctx | null>(null);

export function useApp() {
  const c = useContext(AppCtx);
  if (!c) throw new Error("useApp must be used inside <AppProvider>");
  return c;
}

/** Runs fn now and then every `ms`, while the component is mounted. */
export function usePoll(fn: () => unknown, ms: number, deps: unknown[]) {
  const saved = useRef(fn);
  saved.current = fn;
  useEffect(() => {
    saved.current();
    const t = setInterval(() => saved.current(), ms);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}

/** Wraps an async action: blocks double clicks, shows errors as a toast. */
export function useBusy() {
  const { toast } = useApp();
  const [busy, setBusy] = useState(false);
  const run = useCallback(async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try { await fn(); } catch (e) { toast((e as Error).message, true); } finally { setBusy(false); }
  }, [toast]);
  return [busy, run] as const;
}

export function AppProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [user, setUserState] = useState<User | null>(null);
  const [checked, setChecked] = useState(false);
  const [ref, setRef] = useState<Reference | null>(null);
  const [status, setStatus] = useState<Status | null>(null);
  const [campaigns, setCampaigns] = useState<CampaignOut[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [toastMsg, setToastMsg] = useState<{ msg: string; bad: boolean } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isPublic = PUBLIC.includes(path);

  const toast = useCallback((msg: string, bad = false) => {
    setToastMsg({ msg, bad });
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setToastMsg(null), bad ? 5000 : 2600);
  }, []);

  const goToLogin = useCallback(() => {
    setUserState(null);
    setStatus(null);
    setCampaigns([]);
    if (!PUBLIC.includes(here())) router.replace("/login?next=" + encodeURIComponent(here()));
  }, [router]);

  const setUser = useCallback((u: User | null) => {
    setUserState(u);
    if (u?.mustChangePassword && here() !== "/account") router.replace("/account");
  }, [router]);

  // who is signed in?
  useEffect(() => {
    api.csrf().then(() => api.me()).then(u => setUser(u)).catch(() => goToLogin()).finally(() => setChecked(true));
    const out = () => { toast("Your session ended. Sign in again.", true); goToLogin(); };
    const pw = () => router.replace("/account");
    window.addEventListener(SIGNED_OUT, out);
    window.addEventListener(PASSWORD_REQUIRED, pw);
    return () => { window.removeEventListener(SIGNED_OUT, out); window.removeEventListener(PASSWORD_REQUIRED, pw); };
  }, [goToLogin, router, setUser, toast]);

  const ready = !!user && !user.mustChangePassword;
  const refresh = useCallback(async () => {
    if (!ready) return;
    try {
      const [s, c] = await Promise.all([api.status(), api.campaigns()]);
      setStatus(s); setCampaigns(c); setError(null);
    } catch (e) { setError((e as Error).message); }
  }, [ready]);

  useEffect(() => { if (ready && !ref) api.reference().then(setRef).catch(e => setError(e.message)); }, [ready, ref]);
  usePoll(refresh, 3000, [refresh]);

  const signOut = useCallback(async () => {
    try { await api.logout(); } catch { /* signed out anyway */ }
    goToLogin();
    toast("Signed out");
  }, [goToLogin, toast]);

  const countryName = useCallback((code: string) => ref?.countries.find(c => c.code === code)?.name || code, [ref]);
  const langName = useCallback((l?: string) => (l && ref?.languages[l]) || l || "", [ref]);
  const can = { edit: user?.role === "ADMIN" || user?.role === "EDITOR", admin: user?.role === "ADMIN" };

  let content: ReactNode = children;
  if (!isPublic && (!checked || !user)) content = <div className="loading">Checking your session…</div>;
  else if (!isPublic && user?.mustChangePassword && path !== "/account") content = <div className="loading">Opening your account…</div>;

  return (
    <AppCtx.Provider value={{ user, can, ref, status, campaigns, error, refresh, setUser, signOut, toast, countryName, langName }}>
      {content}
      {toastMsg && <div className="toast" role="status" style={toastMsg.bad ? { background: "var(--bad)" } : undefined}>{toastMsg.msg}</div>}
    </AppCtx.Provider>
  );
}
