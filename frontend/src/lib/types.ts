// Shared data shapes. The API returns exactly these (plus the *Out extras).

export type Category = "customer" | "partner" | "vendor" | "competitor" | "irrelevant";
export type LeadStatus =
  | "analyzing" | "classified" | "review" | "no_email" | "suppressed" | "approved"
  | "sending" | "sent" | "replied" | "bounced" | "failed" | "rejected" | "skipped";
export type CampaignStatus = "draft" | "running" | "paused" | "done";
export type Intent = "interested" | "question" | "not_interested" | "unsubscribe" | "out_of_office" | "bounce";
export type Source = "claude" | "rules" | "you" | "template";

export interface Profile { id: string; name: string; why: string; keywords: string[]; on: boolean }
export interface Strategy {
  offer: string;
  core: string;
  source: "claude" | "rules";
  profiles: Profile[];
  localKeywords: Record<string, string[]>;
  signals: { competitor?: string[]; vendor?: string[]; partner?: string[] };
}
export interface Sender { name: string; company: string; email: string; website: string; role: string; phone: string; address: string }
export interface Query { q: string; country: string; gl: string; location: string }

export interface Campaign {
  id: string;
  name: string;
  description: string;
  goal: string;
  countries: string[];
  regions: Record<string, string[]>;
  strategy: Strategy;
  pitch: string;
  sender: Sender;
  tone: string;
  emailLanguage: "local" | "english";
  targetCustomers: number;
  maxSites: number;
  status: CampaignStatus;
  queries: Query[];
  queryIndex: number;
  current?: { country: string; location: string; query: string } | null;
  error?: string | null;
  createdAt: string;
  startedAt?: string;
  sending: boolean;
}

export interface Draft { subject: string; body: string; language: string; source: Source }
export interface SentInfo { at: string; messageId: string; dryRun: boolean; to: string; subject: string; text: string }

export interface Lead {
  id: string;
  campaignId: string;
  domain: string;
  url: string;
  company: string;
  country: string;
  location: string;
  query: string;
  snippet: string;
  createdAt: string;
  updatedAt?: string;
  status: LeadStatus;
  category: Category | null;
  confidence?: number;
  score?: number;
  reason?: string;
  categorySource?: Source;
  industry?: string | null;
  title?: string;
  siteSummary?: string;
  lang?: string;
  emails?: string[];
  pages?: string[];
  email?: string | null;
  draft?: Draft;
  sent?: SentInfo;
  approvedAt?: string;
  error?: string | null;
  contactFirst?: string;
  templateId?: string | null;
  openedAt?: string | null;
  openCount?: number;
}

export interface Answer { at: string; body: string; messageId: string; dryRun: boolean }
export interface Reply {
  id: string;
  leadId: string;
  campaignId: string;
  company: string;
  domain: string;
  lang: string;
  from: string;
  fromName: string;
  subject: string;
  text: string;
  messageId: string;
  receivedAt: string;
  intent: Intent;
  summary: string;
  translation: string | null;
  draft: string;
  analysisSource: string;
  unread: boolean;
  handled: boolean;
  simulated: boolean;
  answers?: Answer[];
}

export interface Activity { id: string; campaignId: string; kind: string; text: string; at: string; leadId?: string }
export interface Suppression { value: string; reason: string; at: string }

export interface Stats {
  found: number; analysed: number;
  customer: number; partner: number; vendor: number; competitor: number; irrelevant: number; unreadable: number;
  review: number; noEmail: number; approved: number; sent: number; replied: number; bounced: number; rejected: number;
}

export interface CampaignOut extends Omit<Campaign, "queries"> {
  queryCount: number;
  searched: string[];
  stats: Stats;
  activity?: Activity[];
}
export interface LeadOut extends Lead { footer: string | null }
export interface ReplyOut extends Reply { campaignName?: string; leadEmail?: string | null; sentEmail: SentInfo | null; dryRun?: boolean }

export interface Mode {
  database: string;
  search: string;
  ai: "claude" | "rules";
  aiModel: string | null;
  email: "smtp" | "dry-run";
  replies: "imap" | "simulated" | "off";
  dailySendLimit: number;
  sendIntervalSeconds: number;
  fromAddress: string | null;
  replyAddress: string | null;
}
/** Mail configuration (Settings). Passwords are never returned, only whether one is saved. */
export interface MailSettings {
  source: "settings" | "server";
  encryptionReady: boolean;
  passwordProblem: boolean;
  smtpHost: string; smtpPort: number; smtpSsl: boolean; smtpUser: string; smtpPassSet: boolean;
  fromAddress: string; sendReal: boolean;
  imapHost: string; imapPort: number; imapSsl: boolean; imapUser: string; imapPassSet: boolean; imapMailbox: string;
  effectiveFrom: string | null; effectiveReply: string | null;
  updatedAt: string | null; updatedBy: string | null;
}
export type MailInput = Omit<MailSettings, "source" | "encryptionReady" | "passwordProblem" | "smtpPassSet" | "imapPassSet" | "effectiveFrom" | "effectiveReply" | "updatedAt" | "updatedBy">
  & { smtpPass: string; imapPass: string };
export interface MailTest { ok: boolean; message: string }

export interface Status { mode: Mode; sentToday: number; reviewCount: number; unreadReplies: number; unreadOpens: number }

/* ---------- templates & notifications ---------- */
export interface Template { id: string; campaignId: string; name: string; type: string; language: string; greeting: string; subject: string; body: string; createdAt: string; updatedAt: string }
export type TemplateInput = Omit<Template, "id" | "createdAt" | "updatedAt">;
export interface Notification { leadId: string; campaignId: string; campaignName: string; company: string; email: string | null; openedAt: string; openCount: number; seen: boolean }
export interface NotificationList { unread: number; items: Notification[] }

export interface CountryRef { code: string; name: string; lang: string; gl: string; region: string }
export interface Reference {
  countries: CountryRef[];
  regions: Record<string, { label: string; list: string[] }>;
  languages: Record<string, string>;
  categories: { id: Category; label: string; desc: string }[];
  goals: string[];
  tones: { id: string; label: string; desc: string }[];
}

export interface NewCampaignInput {
  name: string;
  description: string;
  goal: string;
  countries: string[];
  regions: Record<string, string[]>;
  strategy: Strategy;
  sender: Sender;
  tone: string;
  emailLanguage: "local" | "english";
  targetCustomers: number;
  maxSites: number;
  start?: boolean;
}

/* ---------- accounts ---------- */
export type Role = "ADMIN" | "EDITOR" | "VIEWER";
/** The signed-in user (from /api/auth/me). */
export interface User { id: string; email: string; name: string; role: Role; mustChangePassword: boolean }
/** A team member, as admins see them. */
export interface TeamMember { id: string; email: string; name: string; role: Role; enabled: boolean; mustChangePassword: boolean; lastLoginAt: string | null; createdAt: string; locked: boolean }
export interface AuditEvent { id: string; at: string; userEmail: string | null; action: string; target: string | null; detail: string | null; ip: string | null }
