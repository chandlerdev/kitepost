"use client";
import type { CampaignOut, Category, Intent, LeadStatus } from "@/lib/types";
import { CAT, INTENT, ST } from "@/lib/format";
import { useApp } from "./AppContext";

export const CatPill = ({ c }: { c: Category | null }) => <span className={`pill c-${c || "none"}`}>{c ? CAT[c] : "Analysing"}</span>;
export const StPill = ({ s }: { s: LeadStatus }) => <span className={`pill st-${s}`}>{ST[s] || s}</span>;
export const IntentPill = ({ i, small }: { i: Intent; small?: boolean }) => <span className={`pill i-${i}`} style={small ? { fontSize: ".68rem" } : undefined}>{INTENT[i]}</span>;

export function CampaignStatus({ c }: { c: Pick<CampaignOut, "status"> }) {
  const m = ({ running: ["s-sent", "Searching"], paused: ["s-queued", "Paused"], done: ["s-manual", "Search finished"], draft: ["s-queued", "Draft"] } as Record<string, string[]>)[c.status] || ["s-queued", c.status];
  return <span className={`pill ${m[0]}`}><span className={`dot${c.status === "running" ? " live" : ""}`} />{m[1]}</span>;
}

export function ModeBanner() {
  const { status } = useApp();
  const m = status?.mode;
  if (!m) return null;
  const parts: string[] = [];
  if (m.search === "demo") parts.push("companies are fictional (.example)");
  if (m.email !== "smtp") parts.push("emails are recorded, not sent");
  if (m.replies === "simulated") parts.push("some replies are simulated");
  if (!parts.length) return null;
  return <div className="demo">Demo mode: {parts.join(", ")}. See Settings to connect real search and email.</div>;
}

export function PageError({ message }: { message: string }) {
  return <div className="load-error"><h2>This page couldn&apos;t load</h2><p>{message}</p></div>;
}

export const Icon = {
  camp: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="2" y="3" width="12" height="10" rx="2" /><path d="M2 6.5h12" /></svg>,
  plus: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M8 3v10M3 8h10" /></svg>,
  review: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M3 8.5l3 3 7-7" /></svg>,
  inbox: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M2 9l2-6h8l2 6v4H2z" /><path d="M2 9h3.5l1 1.5h3l1-1.5H14" /></svg>,
  template: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="2.5" y="2" width="11" height="12" rx="2" /><path d="M5 5.5h6M5 8h6M5 10.5h3.5" /></svg>,
  bell: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M4 11V7.5a4 4 0 018 0V11l1 1.5H3z" /><path d="M6.5 14h3" /></svg>,
  gear: <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6"><circle cx="8" cy="8" r="2.2" /><path d="M8 1.8v1.6M8 12.6v1.6M1.8 8h1.6M12.6 8h1.6M3.6 3.6l1.1 1.1M11.3 11.3l1.1 1.1M3.6 12.4l1.1-1.1M11.3 4.7l1.1-1.1" /></svg>
};
