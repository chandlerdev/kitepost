"use client";
// Dropdown of one campaign's templates. Picking one fills the lead's email from it.
import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Template } from "@/lib/types";

export function useTemplates(campaignId: string) {
  const [list, setList] = useState<Template[] | null>(null);
  useEffect(() => {
    setList(null);
    if (campaignId) api.templates(campaignId).then(setList).catch(() => setList([]));
  }, [campaignId]);
  return list;
}

export function TemplatePicker({ id, templates, value, disabled, onPick }: {
  id: string; templates: Template[] | null; value?: string | null; disabled?: boolean; onPick: (templateId: string) => void;
}) {
  if (templates && !templates.length) {
    return <div className="field"><label>Template</label><span className="hint">This campaign has no templates yet. <Link href="/templates">Create one in Templates</Link>, then choose it here.</span></div>;
  }
  return (
    <div className="field">
      <label htmlFor={id}>Template</label>
      <select id={id} value={value || ""} disabled={disabled || !templates} onChange={e => e.target.value && onPick(e.target.value)}>
        <option value="">Choose a template…</option>
        {(templates || []).map(t => <option key={t.id} value={t.id}>{t.name} · {t.type}</option>)}
      </select>
    </div>
  );
}
