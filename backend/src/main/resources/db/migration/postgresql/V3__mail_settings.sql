-- Mail configuration set in Settings (one row, id 'main'). Passwords are stored encrypted (AES-256-GCM), never as text.

CREATE TABLE mail_settings (
  id             VARCHAR(20)  PRIMARY KEY,
  smtp_host      VARCHAR(255) NOT NULL DEFAULT '',
  smtp_port      INTEGER      NOT NULL DEFAULT 587,
  smtp_ssl       BOOLEAN      NOT NULL DEFAULT FALSE,
  smtp_user      VARCHAR(320) NOT NULL DEFAULT '',
  smtp_pass_enc  VARCHAR(1000) NOT NULL DEFAULT '',
  from_address   VARCHAR(320) NOT NULL DEFAULT '',
  send_real      BOOLEAN      NOT NULL DEFAULT FALSE,
  imap_host      VARCHAR(255) NOT NULL DEFAULT '',
  imap_port      INTEGER      NOT NULL DEFAULT 993,
  imap_ssl       BOOLEAN      NOT NULL DEFAULT TRUE,
  imap_user      VARCHAR(320) NOT NULL DEFAULT '',
  imap_pass_enc  VARCHAR(1000) NOT NULL DEFAULT '',
  imap_mailbox   VARCHAR(200) NOT NULL DEFAULT 'INBOX',
  updated_at     TIMESTAMPTZ  NOT NULL,
  updated_by     VARCHAR(320),
  version        BIGINT       NOT NULL DEFAULT 0
);
