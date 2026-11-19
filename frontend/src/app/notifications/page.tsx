"use client";
// Notifications: which companies opened your email. Replies are on the Replies page.
import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { ago } from "@/lib/format";
import type { NotificationList } from "@/lib/types";
import { useApp, usePoll } from "@/components/AppContext";

export default function NotificationsPage() {
  const { refresh } = useApp();
  const [data, setData] = useState<NotificationList | null>(null);

  const load = useCallback(async () => {
    const d = await api.notifications();
    setData(d);
    if (d.unread > 0) { await api.markNotificationsSeen().catch(() => {}); refresh(); }
  }, [refresh]);
  usePoll(() => { load().catch(() => {}); }, 15000, [load]);

  return (
    <>
      <div className="head">
        <div>
          <span className="eyebrow">Notifications</span><h1>Emails that were opened</h1>
          <p className="muted">Shows which company opened your email. Replies are on the Replies page.</p>
        </div>
      </div>
      <p className="hint">“Opened” is likely, not certain: some mail apps load images automatically and some people block them. Opens are only recorded for emails sent after the app has a public web address (PUBLIC_URL in .env).</p>
      {data && !data.items.length && <div className="empty panel"><b>No opens recorded yet</b><span>When a company opens an email you sent, it appears here.</span></div>}
      <div className="queue">
        {data?.items.map(n => (
          <article key={n.leadId} className="qcard">
            <span />
            <div className="qbody">
              <div className="qhead">
                <div style={{ minWidth: 0 }}>
                  <h3>{n.company} opened your email</h3>
                  <div className="qmeta"><span>{n.campaignName}</span>{n.email && <span className="mono">{n.email}</span>}<span>{ago(n.openedAt)}{n.openCount > 1 ? ` · opened ${n.openCount} times` : ""}</span></div>
                </div>
                {!n.seen && <span className="pill s-replied">New</span>}
              </div>
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
