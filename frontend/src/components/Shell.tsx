"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useApp } from "./AppContext";
import { Icon } from "./ui";

/** "Jake Zebaida" -> "JZ", "admin" -> "A" */
const initials = (name: string) => name.trim().split(/\s+/).slice(0, 2).map(w => w[0] || "").join("") || "?";

export function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const { status, user, can, signOut } = useApp();
  if (path === "/login" || !user || user.mustChangePassword) return <main className="auth-main">{children}</main>;
  const display = user.name && !/^u_[0-9a-f]+$/i.test(user.name) ? user.name : user.email.split("@")[0];   // never show an internal id
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
          {can.edit && <Link href="/campaigns/new" {...cur("/campaigns/new")}>{Icon.plus} New campaign</Link>}
          <Link href="/review" {...cur("/review")}>{Icon.review} Review &amp; send {status?.reviewCount ? <span className="badge" style={{ background: "var(--warn)" }}>{status.reviewCount}</span> : null}</Link>
          <Link href="/templates" {...cur("/templates")}>{Icon.template} Templates</Link>
          <Link href="/inbox" {...cur("/inbox")}>{Icon.inbox} Replies {status?.unreadReplies ? <span className="badge">{status.unreadReplies}</span> : null}</Link>
          <Link href="/notifications" {...cur("/notifications")}>{Icon.bell} Notifications {status?.unreadOpens ? <span className="badge">{status.unreadOpens}</span> : null}</Link>
          <Link href="/settings" {...cur("/settings")}>{Icon.gear} Settings</Link>
        </nav>
        <div className="whoami">
          <Link href="/account" className="who" title="Your account">
            <span className="avatar" aria-hidden="true">{initials(display)}</span>
            <span className="who-text"><b>{display}</b><small>{user.role === "ADMIN" ? "Admin" : user.role === "EDITOR" ? "Editor" : "Viewer"}</small></span>
          </Link>
          <button className="signout" onClick={signOut} title="Sign out" aria-label="Sign out">{Icon.logout}</button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
