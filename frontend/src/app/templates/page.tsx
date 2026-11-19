"use client";
// Templates: write the emails for each campaign by hand. They appear in the dropdown in Review & send.
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ago, short } from "@/lib/format";
import type { Template, TemplateInput } from "@/lib/types";
import { useApp, useBusy } from "@/components/AppContext";

const TYPES: [string, string][] = [["friendly", "Friendly"], ["formal", "Formal"], ["request", "Request"], ["follow-up", "Follow-up"], ["other", "Other"]];
const PLACEHOLDERS = ["{company}", "{domain}", "{sender}", "{senderCompany}", "{role}", "{pitch}"];

const blank = (campaignId: string, language: string): TemplateInput => ({
  campaignId, name: "", type: "friendly", language, greeting: "Hello {company} team,", subject: "", body: ""
});

export default function TemplatesPage() {
  const { campaigns, ref, can, toast } = useApp();
  const [campaignId, setCampaignId] = useState<string>("");
  const [list, setList] = useState<Template[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [form, setForm] = useState<(TemplateInput & { id?: string }) | null>(null);
  const [busy, run] = useBusy();

  useEffect(() => { if (!campaignId && campaigns.length) setCampaignId(campaigns[0].id); }, [campaigns, campaignId]);
  const load = useCallback(async () => {
    if (!campaignId) return;
    setList(await api.templates(campaignId)); setLoaded(true);
  }, [campaignId]);
  useEffect(() => { setLoaded(false); setForm(null); load().catch(e => toast(e.message, true)); }, [load, toast]);

  const campaign = campaigns.find(c => c.id === campaignId);
  const langs = Object.entries(ref?.languages || { en: "English" });
  const set = (patch: Partial<TemplateInput>) => setForm(f => (f ? { ...f, ...patch } : f));
  const save = () => run(async () => {
    if (!form) return;
    const { id, ...input } = form;
    if (id) await api.updateTemplate(id, input); else await api.createTemplate(input);
    toast(id ? "Template updated" : "Template added to " + (campaign?.name || "the campaign"));
    setForm(null); await load();
  });

  if (!campaigns.length) {
    return <div className="load-error"><h2>No campaigns yet</h2><p className="muted">Templates belong to a campaign. Create a campaign first, then write its emails here.</p></div>;
  }
  return (
    <>
      <div className="head">
        <div>
          <span className="eyebrow">Templates</span><h1>Email templates by campaign</h1>
          <p className="muted">Write the emails for each campaign once. In Review &amp; send you choose one of them for each company, check it, and send.</p>
        </div>
        {can.edit && campaignId && !form && <button className="btn primary" onClick={() => setForm(blank(campaignId, "en"))}>+ New template</button>}
      </div>

      <div className="chips" role="group" aria-label="Campaign">
        {campaigns.map(c => <button key={c.id} className="chip" aria-pressed={c.id === campaignId} onClick={() => setCampaignId(c.id)}>{short(c.name, 32)}</button>)}
      </div>

      {form && (
        <section className="panel">
          <h3>{form.id ? "Edit template" : "New template"} <span className="muted" style={{ fontWeight: 400 }}>· {campaign?.name}</span></h3>
          <div className="grid2">
            <div className="field"><label htmlFor="t-name">Template name</label><input id="t-name" type="text" maxLength={200} value={form.name} onChange={e => set({ name: e.target.value })} placeholder="e.g. Friendly intro" /></div>
            <div className="field"><label htmlFor="t-type">Email type</label>
              <select id="t-type" value={form.type} onChange={e => set({ type: e.target.value })}>{TYPES.map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          </div>
          <div className="grid2">
            <div className="field"><label htmlFor="t-lang">Language</label>
              <select id="t-lang" value={form.language} onChange={e => set({ language: e.target.value })}>{langs.map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
            <div className="field"><label htmlFor="t-greet">Greeting</label><input id="t-greet" type="text" maxLength={300} value={form.greeting} onChange={e => set({ greeting: e.target.value })} /></div>
          </div>
          <div className="field"><label htmlFor="t-subj">Subject</label><input id="t-subj" type="text" maxLength={200} value={form.subject} onChange={e => set({ subject: e.target.value })} placeholder="e.g. Coffee for {company}" /></div>
          <div className="field"><label htmlFor="t-body">Message</label><textarea id="t-body" style={{ minHeight: 200 }} maxLength={5000} value={form.body} onChange={e => set({ body: e.target.value })} placeholder={"Write the email here. The greeting is added above it, and your sign-off belongs at the end, for example:\n\nBest regards,\n{sender}, {senderCompany}"} /></div>
          <p className="hint">Placeholders filled in for each company: {PLACEHOLDERS.map(p => <code key={p} className="mono" style={{ marginRight: 8 }}>{p}</code>)}. The unsubscribe line and your address are added automatically when sending.</p>
          <div className="qactions">
            <button className="btn primary" disabled={busy} onClick={save}>{form.id ? "Save changes" : "Add template"}</button>
            <button className="btn ghost" disabled={busy} onClick={() => setForm(null)}>Cancel</button>
          </div>
        </section>
      )}

      {!form && loaded && !list.length && <div className="empty panel"><b>No templates for {campaign?.name} yet</b><span>{can.edit ? "Click “New template” to write the first one." : "An editor or admin can add templates."}</span></div>}
      <div className="queue">
        {list.map(t => (
          <article key={t.id} className="qcard">
            <span />
            <div className="qbody">
              <div className="qhead">
                <div style={{ minWidth: 0 }}><h3>{t.name}</h3><div className="qmeta"><span>{TYPES.find(x => x[0] === t.type)?.[1] || t.type}</span><span>{ref?.languages[t.language] || t.language}</span><span>Updated {ago(t.updatedAt)}</span></div></div>
              </div>
              <p><b>{t.subject}</b></p>
              <div className="preview-mail">{t.greeting + "\n\n" + t.body}</div>
              {can.edit && (
                <div className="qactions">
                  <button className="btn" onClick={() => setForm({ ...t })}>Edit</button>
                  <button className="btn ghost" disabled={busy} onClick={() => { if (window.confirm(`Delete the template “${t.name}”?`)) run(async () => { await api.deleteTemplate(t.id); toast("Template deleted"); await load(); }); }}>Delete</button>
                </div>
              )}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
