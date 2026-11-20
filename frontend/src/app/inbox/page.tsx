"use client";
// Replies: intent, summary, the email they answer, and a box to write an answer.
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { ago, short } from "@/lib/format";
import type { ReplyOut } from "@/lib/types";
import { useApp, useBusy, usePoll } from "@/components/AppContext";
import { IntentPill, ModeBanner } from "@/components/ui";
import { LeadDrawer } from "@/components/LeadDrawer";

export default function InboxPage() {
  const { status, toast, refresh, langName, can } = useApp();
  const [list, setList] = useState<ReplyOut[]>([]);
  const [sel, setSel] = useState<string | null>(null);
  const [answer, setAnswer] = useState("");
  const [openLead, setOpenLead] = useState<string | null>(null);
  const [busy, run] = useBusy();
  const ta = useRef<HTMLTextAreaElement>(null);
  const m = status?.mode;

  const load = useCallback(async () => { setList(await api.replies()); }, []);
  usePoll(() => { if (document.activeElement !== ta.current) load().catch(() => {}); }, 3000, [load]);

  const r = list.find(x => x.id === sel) || list[0];
  useEffect(() => {
    if (!r) return;
    setAnswer("");
    if (r.unread) api.updateReply(r.id, { unread: false }).then(() => { load(); refresh(); });
    // only when the selected reply changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [r?.id]);

  const needsAnswer = can.edit && r && ["interested", "question"].includes(r.intent);
  return (
    <>
      <ModeBanner />
      <div className="head">
        <div>
          <span className="eyebrow">Replies</span><h1>Replies to your emails</h1>
          <p className="muted">{m?.replies === "imap" ? "Your inbox is checked regularly. Each reply is matched to the email it answers." : m?.replies === "simulated" ? "Demo replies arrive a little while after an email is recorded." : "Reply tracking is off. Add IMAP settings to track replies."}</p>
        </div>
        {can.edit && m?.replies === "imap" && <button className="btn" disabled={busy} onClick={() => run(async () => { await api.checkInbox(); toast("Inbox checked"); await load(); })}>Check inbox now</button>}
      </div>
      <div className="inbox">
        <div className="threads">
          {list.length ? list.map(x => (
            <button key={x.id} className="thread" aria-current={x.id === r?.id} onClick={() => setSel(x.id)}>
              <span className="r1"><b>{short(x.fromName, 34)}</b>{x.handled ? <span className="handled">Answered</span> : x.unread ? <span className="new">New</span> : <span className="muted" style={{ fontSize: ".72rem" }}>{ago(x.receivedAt)}</span>}</span>
              <span className="r1"><span className="mono muted">{x.domain}</span><IntentPill i={x.intent} small /></span>
              <span className="sn">{x.summary}</span>
            </button>
          )) : <div className="empty"><b>No replies yet</b><span>Replies appear here when companies answer your emails.</span></div>}
        </div>
        <div className="read">
          {!r ? <div className="empty">Select a reply</div> : (
            <>
              <div className="head">
                <div style={{ minWidth: 0 }}><h2>{r.fromName}</h2><span className="muted">{r.company} · <span className="mono">{r.from}</span> · {ago(r.receivedAt)}</span></div>
                <IntentPill i={r.intent} />
              </div>
              <div className="summary"><b>Summary:</b> {r.summary}{["unsubscribe", "not_interested"].includes(r.intent) && <><br /><b>Added to your do-not-contact list.</b></>}</div>
              <div className="msg">
                <span className="eyebrow">Their reply{r.translation ? ` · translated from ${langName(r.lang)}` : ""}{r.simulated ? " · demo" : ""}</span>
                <div className="body">{r.translation || r.text}</div>
                {r.translation && <details className="orig"><summary>Show original ({langName(r.lang)})</summary><div className="body">{r.text}</div></details>}
              </div>
              {r.sentEmail && <details className="orig"><summary>Your email ({r.sentEmail.subject})</summary><div className="body">{r.sentEmail.text}</div></details>}
              {(r.answers || []).map(a => <div key={a.messageId} className="msg"><span className="eyebrow">Your answer · {ago(a.at)}{a.dryRun ? " · dry run" : ""}</span><div className="body">{a.body}</div></div>)}
              {needsAnswer && (
                <div className="field draft">
                  <label htmlFor="ans">{r.answers?.length ? "Another answer" : "Your answer"}, in {langName(r.lang)}</label>
                  <textarea id="ans" ref={ta} value={answer} placeholder="Write your answer here…" onChange={e => setAnswer(e.target.value)} />
                </div>
              )}
              <div className="qactions">
                {needsAnswer && <>
                  <button className="btn primary" disabled={busy || !answer.trim()} onClick={() => run(async () => { const res = await api.answer(r.id, answer); toast(res.dryRun ? "Answer recorded (dry run, not sent)" : "Answer sent"); await load(); setAnswer(""); })}>Send answer</button>
                </>}
                {can.edit && <button className="btn" disabled={busy} onClick={() => run(async () => { await api.updateReply(r.id, { handled: !r.handled }); await load(); })}>{r.handled ? "Mark as open" : "Mark as handled"}</button>}
                <button className="btn ghost" onClick={() => setOpenLead(r.leadId)}>Lead details</button>
              </div>
              {can.edit && <p className="hint">Answers are sent from your campaign&apos;s sender address, in the same email thread. Nothing is sent until you press Send answer.</p>}
            </>
          )}
        </div>
      </div>
      {openLead && <LeadDrawer leadId={openLead} onClose={() => setOpenLead(null)} onChanged={load} />}
    </>
  );
}
