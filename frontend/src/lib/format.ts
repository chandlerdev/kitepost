import type { Category, Intent, LeadStatus } from "./types";

export const fmt = (n?: number) => Math.round(n || 0).toLocaleString("en-US");
export const short = (s: string | undefined | null, n: number) => (s && s.length > n ? s.slice(0, n - 1) + "…" : s || "");
export function ago(iso?: string) {
  if (!iso) return "";
  const s = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  return s < 60 ? s + "s ago" : s < 3600 ? Math.round(s / 60) + "m ago" : s < 86400 ? Math.round(s / 3600) + "h ago" : Math.round(s / 86400) + "d ago";
}

export const CAT: Record<Category, string> = { customer: "Customer", partner: "Partner", vendor: "Vendor", competitor: "Competitor", irrelevant: "Not relevant" };
export const CAT_PLURAL: Record<Category | "all", string> = { customer: "Customers", partner: "Partners", vendor: "Vendors", competitor: "Competitors", irrelevant: "Not relevant", all: "All" };
export const CAT_COLOR: Record<Category, string> = { customer: "var(--ok)", partner: "var(--info)", vendor: "var(--warn)", competitor: "var(--bad)", irrelevant: "var(--line)" };
export const CATS = Object.keys(CAT) as Category[];
export const ST: Record<LeadStatus, string> = { analyzing: "Analysing", classified: "Marked", review: "Needs review", no_email: "No email found", suppressed: "Do not contact", approved: "Queued to send", sending: "Sending", sent: "Sent", replied: "Replied", bounced: "Bounced", failed: "Failed", rejected: "Rejected", skipped: "Unreadable site" };
export const INTENT: Record<Intent, string> = { interested: "Interested", question: "Has questions", not_interested: "Not interested", unsubscribe: "Unsubscribe", out_of_office: "Out of office", bounce: "Bounced" };
export const SRC: Record<string, string> = { claude: "Claude", rules: "keyword rules", you: "you", template: "template" };

export async function copyText(text: string, el?: HTMLTextAreaElement | null) {
  try { await navigator.clipboard.writeText(text); return true; }
  catch { el?.select(); return false; }
}

/** "Ohio", "Ohio and Texas", or "5 states" */
export function regionSummary(list: string[] | undefined, label: string) {
  if (!list?.length) return "";
  return list.length <= 2 ? list.join(" and ") : `${list.length} ${label}`;
}
