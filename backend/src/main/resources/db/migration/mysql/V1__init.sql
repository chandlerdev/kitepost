-- Kitepost schema for MySQL 8 (InnoDB, utf8mb4). Nested objects (strategy, sender, draft...) are JSON text.

CREATE TABLE users (
  id                    VARCHAR(40)  PRIMARY KEY,
  email                 VARCHAR(320) NOT NULL,
  name                  VARCHAR(200) NOT NULL,
  password_hash         VARCHAR(200) NOT NULL,
  role                  VARCHAR(20)  NOT NULL,
  enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
  must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
  failed_logins         INTEGER      NOT NULL DEFAULT 0,
  locked_until          DATETIME(6),
  last_login_at         DATETIME(6),
  created_at            DATETIME(6)  NOT NULL,
  version               BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE campaigns (
  id                VARCHAR(40)  PRIMARY KEY,
  name              VARCHAR(300) NOT NULL,
  description       LONGTEXT         NOT NULL,
  goal              VARCHAR(40)  NOT NULL,
  countries         LONGTEXT         NOT NULL,
  regions           LONGTEXT         NOT NULL,
  strategy          LONGTEXT         NOT NULL,
  pitch             LONGTEXT         NOT NULL,
  sender            LONGTEXT         NOT NULL,
  tone              VARCHAR(20)  NOT NULL,
  email_language    VARCHAR(20)  NOT NULL,
  target_customers  INTEGER      NOT NULL,
  max_sites         INTEGER      NOT NULL,
  status            VARCHAR(20)  NOT NULL,
  queries           LONGTEXT         NOT NULL,
  query_index       INTEGER      NOT NULL DEFAULT 0,
  current_search    LONGTEXT,
  error             VARCHAR(1000),
  sending           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by        VARCHAR(40),
  created_at        DATETIME(6)  NOT NULL,
  started_at        DATETIME(6),
  version           BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX campaigns_status_idx ON campaigns (status);

CREATE TABLE leads (
  id               VARCHAR(40)   PRIMARY KEY,
  campaign_id      VARCHAR(40)   NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
  domain           VARCHAR(255)  NOT NULL,
  url              VARCHAR(2000) NOT NULL,
  company          VARCHAR(300)  NOT NULL,
  country          VARCHAR(2)    NOT NULL,
  location         VARCHAR(120),
  query            VARCHAR(500),
  snippet          LONGTEXT,
  status           VARCHAR(20)   NOT NULL,
  category         VARCHAR(20),
  confidence       DOUBLE,
  score            DOUBLE,
  reason           LONGTEXT,
  category_source  VARCHAR(20),
  industry         VARCHAR(200),
  title            VARCHAR(500),
  site_summary     LONGTEXT,
  lang             VARCHAR(5),
  emails           LONGTEXT,
  pages            LONGTEXT,
  email            VARCHAR(320),
  draft            LONGTEXT,
  sent             LONGTEXT,
  sent_message_id  VARCHAR(255),
  approved_at      DATETIME(6),
  error            VARCHAR(1000),
  contact_first    VARCHAR(100),
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  version          BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT leads_campaign_domain_uq UNIQUE (campaign_id, domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX leads_campaign_idx ON leads (campaign_id, updated_at);
CREATE INDEX leads_status_idx   ON leads (status, approved_at);
CREATE INDEX leads_message_idx  ON leads (sent_message_id);
CREATE INDEX leads_email_idx    ON leads (email);

CREATE TABLE replies (
  id               VARCHAR(40)  PRIMARY KEY,
  lead_id          VARCHAR(40)  NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
  campaign_id      VARCHAR(40)  NOT NULL,
  company          VARCHAR(300) NOT NULL,
  domain           VARCHAR(255) NOT NULL,
  lang             VARCHAR(5)   NOT NULL,
  from_address     VARCHAR(320) NOT NULL,
  from_name        VARCHAR(320) NOT NULL,
  subject          VARCHAR(1000),
  body             LONGTEXT         NOT NULL,
  message_id       VARCHAR(255),
  received_at      DATETIME(6)  NOT NULL,
  intent           VARCHAR(20)  NOT NULL,
  summary          LONGTEXT,
  translation      LONGTEXT,
  draft            LONGTEXT,
  analysis_source  VARCHAR(20),
  unread           BOOLEAN      NOT NULL DEFAULT TRUE,
  handled          BOOLEAN      NOT NULL DEFAULT FALSE,
  simulated        BOOLEAN      NOT NULL DEFAULT FALSE,
  answers          LONGTEXT,
  version          BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX replies_received_idx ON replies (received_at);
CREATE INDEX replies_message_idx  ON replies (message_id);

CREATE TABLE activity (
  id           VARCHAR(40)   PRIMARY KEY,
  campaign_id  VARCHAR(40)   NOT NULL,
  kind         VARCHAR(30)   NOT NULL,
  text         VARCHAR(1000) NOT NULL,
  lead_id      VARCHAR(40),
  at           DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX activity_campaign_idx ON activity (campaign_id, at);

CREATE TABLE suppression (
  value   VARCHAR(320) PRIMARY KEY,
  reason  VARCHAR(100) NOT NULL,
  at      DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta (
  k  VARCHAR(100)  PRIMARY KEY,
  v  VARCHAR(1000) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_events (
  id          VARCHAR(40)   PRIMARY KEY,
  at          DATETIME(6)   NOT NULL,
  user_id     VARCHAR(40),
  user_email  VARCHAR(320),
  action      VARCHAR(60)   NOT NULL,
  target      VARCHAR(300),
  detail      VARCHAR(1000),
  ip          VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX audit_at_idx ON audit_events (at);

-- Spring Session JDBC (server-side sessions; signing out ends the session everywhere)
CREATE TABLE SPRING_SESSION (
  PRIMARY_ID             CHAR(36)     NOT NULL,
  SESSION_ID             CHAR(36)     NOT NULL,
  CREATION_TIME          BIGINT       NOT NULL,
  LAST_ACCESS_TIME       BIGINT       NOT NULL,
  MAX_INACTIVE_INTERVAL  INT          NOT NULL,
  EXPIRY_TIME            BIGINT       NOT NULL,
  PRINCIPAL_NAME         VARCHAR(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID  CHAR(36)     NOT NULL,
  ATTRIBUTE_NAME      VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES     BLOB        NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
