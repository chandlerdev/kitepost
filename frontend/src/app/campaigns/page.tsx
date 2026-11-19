"use client";
import Link from "next/link";
import { useApp } from "@/components/AppContext";
import { CampaignStatus, Icon, ModeBanner, PageError } from "@/components/ui";
import { fmt } from "@/lib/format";

export default function CampaignsPage() {
  const { campaigns, error, status, can } = useApp();
  if (error && !status) return <PageError message={error} />;
  return (
    <>
      <ModeBanner />
      {!campaigns.length ? (
        <div className="load-error">
          <h2>No campaigns yet</h2>
          <p className="muted">Describe what you sell and who buys it. Kitepost finds those companies, sorts out partners, vendors and competitors, and drafts an email to each customer for you to approve.</p>
          {can.edit ? <Link className="btn primary" href="/campaigns/new" style={{ alignSelf: "flex-start" }}>{Icon.plus} Create your first campaign</Link> : <p className="viewonly">You have view-only access. An editor or admin can create campaigns.</p>}
        </div>
      ) : (
        <>
          <div className="head"><div><span className="eyebrow">Campaigns</span><h1>Your outreach</h1></div>{can.edit && <Link className="btn primary" href="/campaigns/new">{Icon.plus} New campaign</Link>}</div>
          <div className="list">
            {campaigns.map(c => (
              <Link key={c.id} className="ccard" href={`/campaigns/${c.id}`}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}><CampaignStatus c={c} /><span className="muted" style={{ fontSize: ".75rem" }}>{c.countries.join(" · ")}</span></div>
                <h3>{c.name}</h3>
                <div className="row"><span className="muted">Customers found</span><b className="num">{fmt(c.stats.customer)}</b></div>
                <div className="row"><span className="muted">Waiting for review</span><b className="num">{fmt(c.stats.review)}</b></div>
                <div className="row"><span className="muted">Sent · replies</span><b className="num">{fmt(c.stats.sent)} · {fmt(c.stats.replied)}</b></div>
              </Link>
            ))}
          </div>
        </>
      )}
    </>
  );
}
