// Browser client for the Kitepost API (Spring Boot, proxied at /api).
// - Sends the session cookie automatically (same origin).
// - Adds the CSRF token (KITEPOST-XSRF cookie → X-XSRF-TOKEN header) to every POST/PATCH/DELETE.
// - Throws ApiError with the server's plain-language message.
import type { AuditEvent, CampaignOut, Category, LeadOut, MailInput, MailSettings, MailTest, NewCampaignInput, NotificationList, Reference, ReplyOut, Role, Status, Strategy, Suppression, Template, TemplateInput, TeamMember, User } from "./types";

export class ApiError extends Error {
  constructor(message: string, public status: number, public code?: string) { super(message); }
}

/** Fired on window when the session ends (401) or a password change is required. */
export const SIGNED_OUT = "kitepost:signed-out";
export const PASSWORD_REQUIRED = "kitepost:password-required";

/** Set at build time when the app is served under a path, e.g. NEXT_PUBLIC_BASE_PATH=/kitepost. Empty at the domain root. */
export const BASE = process.env.NEXT_PUBLIC_BASE_PATH || "";
const CSRF_COOKIE = "KITEPOST-XSRF";

function csrfCookie(): string | undefined {
  const c = document.cookie.split("; ").find(x => x.startsWith(CSRF_COOKIE + "="));
  return c ? decodeURIComponent(c.slice(CSRF_COOKIE.length + 1)) : undefined;
}
async function loadCsrf() { await fetch(BASE + "/api/auth/csrf", { credentials: "same-origin", cache: "no-store" }).catch(() => {}); }

async function call<T>(method: string, path: string, body?: unknown, retried = false): Promise<T> {
  const unsafe = method !== "GET";
  if (unsafe && !csrfCookie()) await loadCsrf();
  const headers: Record<string, string> = { accept: "application/json" };
  if (body !== undefined) headers["content-type"] = "application/json";
  if (unsafe) headers["X-XSRF-TOKEN"] = csrfCookie() || "";
  let res: Response;
  try {
    res = await fetch(BASE + "/api" + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined, credentials: "same-origin", cache: "no-store" });
  } catch {
    throw new ApiError("Can't reach the server. Check your connection and try again.", 0);
  }
  const data = (await res.json().catch(() => ({}))) as { error?: string; code?: string };
  if (!res.ok) {
    if (res.status === 403 && data.code === "csrf" && !retried) { await loadCsrf(); return call<T>(method, path, body, true); }
    if (res.status === 401 && !path.startsWith("/auth/")) window.dispatchEvent(new Event(SIGNED_OUT));
    if (data.code === "password_change_required") window.dispatchEvent(new Event(PASSWORD_REQUIRED));
    const fallback = res.status === 502 || res.status === 503 || res.status === 504
      ? "The server isn't responding. Try again in a minute." : `Request failed (HTTP ${res.status}).`;
    throw new ApiError(data.error || fallback, res.status, data.code);
  }
  return data as T;
}
const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  Object.entries(o).forEach(([k, v]) => { if (v != null && v !== "") p.set(k, String(v)); });
  const s = p.toString();
  return s ? "?" + s : "";
};

export interface LeadFilter { campaign?: string; category?: string; status?: string; q?: string; limit?: number }
export interface LeadPatch { email?: string; draft?: { subject: string; body: string }; category?: Category; templateId?: string }

export const api = {
  /* auth */
  csrf: loadCsrf,
  me: () => call<{ user: User }>("GET", "/auth/me").then(r => r.user),
  login: async (email: string, password: string) => { const r = await call<{ user: User }>("POST", "/auth/login", { email, password }); await loadCsrf(); return r.user; },
  logout: async () => { await call("POST", "/auth/logout"); await loadCsrf(); },
  changePassword: async (current: string, next: string) => { const r = await call<{ user: User }>("POST", "/auth/password", { current, next }); return r.user; },

  /* team & audit (admins) */
  users: () => call<TeamMember[]>("GET", "/users"),
  createUser: (u: { email: string; name: string; role: Role; password: string }) => call<TeamMember>("POST", "/users", u),
  updateUser: (id: string, patch: Partial<{ name: string; role: Role; enabled: boolean }>) => call<TeamMember>("PATCH", `/users/${id}`, patch),
  resetPassword: (id: string, password: string) => call<TeamMember>("POST", `/users/${id}/reset-password`, { password }),
  audit: (limit = 200) => call<AuditEvent[]>("GET", `/audit?limit=${limit}`),

  /* app */
  status: () => call<Status>("GET", "/status"),
  reference: () => call<Reference>("GET", "/reference"),
  strategy: (input: { name: string; description: string; goal: string; countries: string[] }) => call<Strategy>("POST", "/strategy", input),

  campaigns: () => call<CampaignOut[]>("GET", "/campaigns"),
  campaign: (id: string) => call<CampaignOut>("GET", `/campaigns/${id}`),
  createCampaign: (c: NewCampaignInput) => call<CampaignOut>("POST", "/campaigns", c),
  updateCampaign: (id: string, patch: Partial<{ name: string; tone: string; sending: boolean; targetCustomers: number }>) => call<CampaignOut>("PATCH", `/campaigns/${id}`, patch),
  start: (id: string) => call<CampaignOut>("POST", `/campaigns/${id}/start`),
  pause: (id: string) => call<CampaignOut>("POST", `/campaigns/${id}/pause`),
  findMore: (id: string, add = 25) => call<CampaignOut>("POST", `/campaigns/${id}/more`, { add }),

  leads: (f: LeadFilter) => call<{ total: number; leads: LeadOut[] }>("GET", "/leads" + qs({ ...f })),
  lead: (id: string) => call<LeadOut>("GET", `/leads/${id}`),
  updateLead: (id: string, patch: LeadPatch) => call<LeadOut>("PATCH", `/leads/${id}`, patch),
  approve: (id: string) => call<LeadOut>("POST", `/leads/${id}/approve`),
  approveMany: (ids: string[]) => call<{ approved: number; skipped: { id: string; reason: string }[] }>("POST", "/leads/approve", { ids }),
  reject: (id: string) => call<LeadOut>("POST", `/leads/${id}/reject`),
  unapprove: (id: string) => call<LeadOut>("POST", `/leads/${id}/unapprove`),

  templates: (campaign?: string) => call<Template[]>("GET", "/templates" + qs({ campaign })),
  createTemplate: (t: TemplateInput) => call<Template>("POST", "/templates", t),
  updateTemplate: (id: string, t: TemplateInput) => call<Template>("PUT", `/templates/${id}`, t),
  deleteTemplate: (id: string) => call<unknown>("DELETE", `/templates/${id}`),

  notifications: () => call<NotificationList>("GET", "/notifications"),
  markNotificationsSeen: () => call<unknown>("POST", "/notifications/seen"),

  replies: () => call<ReplyOut[]>("GET", "/replies"),
  updateReply: (id: string, patch: Partial<{ handled: boolean; unread: boolean; draft: string }>) => call<ReplyOut>("PATCH", `/replies/${id}`, patch),
  answer: (id: string, body: string) => call<ReplyOut>("POST", `/replies/${id}/answer`, { body }),
  checkInbox: () => call<{ ok: boolean; imap: boolean }>("POST", "/replies/check"),

  mailSettings: () => call<MailSettings>("GET", "/settings/mail"),
  saveMailSettings: (m: MailInput) => call<MailSettings>("PUT", "/settings/mail", m),
  resetMailSettings: () => call<MailSettings>("DELETE", "/settings/mail"),
  testMailSending: () => call<MailTest>("POST", "/settings/mail/test/sending"),
  testMailReplies: () => call<MailTest>("POST", "/settings/mail/test/replies"),
  suppression: () => call<Suppression[]>("GET", "/suppression"),
  suppress: (value: string) => call<Suppression[]>("POST", "/suppression", { value }),
  unsuppress: (value: string) => call<Suppression[]>("DELETE", `/suppression/${encodeURIComponent(value)}`)
};
