-- Per-campaign email templates, the template chosen for each lead, and email-open tracking.

CREATE TABLE templates (
  id           VARCHAR(40)  PRIMARY KEY,
  campaign_id  VARCHAR(40)  NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
  name         VARCHAR(200) NOT NULL,
  type         VARCHAR(30)  NOT NULL,
  language     VARCHAR(20)  NOT NULL,
  greeting     VARCHAR(300) NOT NULL,
  subject      VARCHAR(200) NOT NULL,
  body         LONGTEXT     NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  updated_at   DATETIME(6)  NOT NULL,
  version      BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX templates_campaign_idx ON templates (campaign_id);

ALTER TABLE leads ADD COLUMN template_id VARCHAR(40);
ALTER TABLE leads ADD COLUMN opened_at   DATETIME(6);
ALTER TABLE leads ADD COLUMN open_count  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE leads ADD COLUMN open_seen   BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX leads_opened_idx ON leads (opened_at);

-- Emails are no longer written by the AI: drafts waiting in Review are cleared so you pick a template instead.
UPDATE leads SET draft = NULL WHERE status = 'review';
