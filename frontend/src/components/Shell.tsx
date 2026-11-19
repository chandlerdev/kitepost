"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useApp } from "./AppContext";
import { Icon } from "./ui";
import { fmt, short } from "@/lib/format";

export function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const { status, campaigns, user, can, signOut } = useApp();
  if (path === "/login" || !user || user.mustChangePassword) return <main className="auth-main">{children}</main>;
  const m = status?.mode;
  const inCampaigns = path.startsWith("/campaigns") && path !== "/campaigns/new";
  const cur = (p: string) => (path === p ? { "aria-current": "page" as const } : {});
  return (
    <div className="app">
      <aside className="rail">
        <div className="brand">
          <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true"><rect width="26" height="26" rx="7" fill="var(--accent)" /><path d="M7 18 L13 6 L19 18 L13 14.5 Z" fill="var(--accent-ink)" /></svg>
          Kitepost
        </div>
        <nav className="nav">
          <Link href="/campaigns" {...(inCampaigns ? { "aria-current": "page" as const } : {})}>{Icon.camp} Campaigns</Link>
          <div className="nav-sub">
            {campaigns.slice(0, 8).map(c => (
              <Link key={c.id} href={`/campaigns/${c.id}`} style={path === `/campaigns/${c.id}` ? { color: "var(--ink)", fontWeight: 600 } : undefined}>
                <span className={`dot${c.status === "running" ? " live" : ""}`} />{short(c.name, 25)}
              </Link>
            ))}
          </div>
          {can.edit && <Link href="/campaigns/new" {...cur("/campaigns/new")}>{Icon.plus} New campaign</Link>}
          <Link href="/review" {...cur("/review")}>{Icon.review} Review &amp; send {status?.reviewCount ? <span className="badge" style={{ background: "var(--warn)" }}>{status.reviewCount}</span> : null}</Link>
          <Link href="/templates" {...cur("/templates")}>{Icon.template} Templates</Link>
          <Link href="/inbox" {...cur("/inbox")}>{Icon.inbox} Replies {status?.unreadReplies ? <span className="badge">{status.unreadReplies}</span> : null}</Link>
          <Link href="/notifications" {...cur("/notifications")}>{Icon.bell} Notifications {status?.unreadOpens ? <span className="badge">{status.unreadOpens}</span> : null}</Link>
          <Link href="/settings" {...cur("/settings")}>{Icon.gear} Settings</Link>
        </nav>
        <div className="whoami">
          <div style={{ minWidth: 0 }}><b>{user.name}</b><span className="muted mono">{user.email}</span></div>
          <span className={`pill ${user.role === "ADMIN" ? "s-replied" : user.role === "EDITOR" ? "s-sent" : "s-queued"}`}>{user.role === "ADMIN" ? "Admin" : user.role === "EDITOR" ? "Editor" : "Viewer"}</span>
          <div className="whoami-actions"><Link href="/account" className="btn sm ghost">Account</Link><button className="btn sm ghost" onClick={signOut}>Sign out</button></div>
        </div>
        {m && (
          <div className="mode">
            <span className="eyebrow">Server</span>
            <div><span>Database</span><b>{m.database}</b></div>
            <div><span>Search</span><b className={m.search === "demo" ? "safe" : "live"}>{m.search === "demo" ? "Demo data" : m.search}</b></div>
            <div><span>Classifying</span><b className={m.ai === "claude" ? "live" : "safe"}>{m.ai === "claude" ? "Claude" : "Keyword rules"}</b></div>
            <div><span>Email</span><b className={m.email === "smtp" ? "live" : "safe"}>{m.email === "smtp" ? "Sending (SMTP)" : "Dry run"}</b></div>
            <div><span>Replies</span><b>{{ imap: "IMAP inbox", simulated: "Simulated", off: "Off" }[m.replies]}</b></div>
            <div><span>Sent today</span><b className="num">{fmt(status?.sentToday)} / {fmt(m.dailySendLimit)}</b></div>
          </div>
        )}
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
