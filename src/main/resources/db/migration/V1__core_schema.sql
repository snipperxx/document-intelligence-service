CREATE TABLE app_user
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Organization tree: ORGANIZATION -> DEPARTMENT -> PROJECT.
-- "path" is a materialized path such as /1/4/9/, so ancestor and subtree
-- lookups are a single indexed query instead of a recursive walk.
CREATE TABLE org_unit
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('ORGANIZATION', 'DEPARTMENT', 'PROJECT')),
    parent_id  BIGINT REFERENCES org_unit (id),
    path       VARCHAR(512),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_org_unit_parent ON org_unit (parent_id);
CREATE INDEX idx_org_unit_path ON org_unit (path varchar_pattern_ops);

-- A role granted on a unit applies to that unit and everything below it.
CREATE TABLE membership
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id),
    unit_id    BIGINT      NOT NULL REFERENCES org_unit (id),
    role       VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_membership_user_unit UNIQUE (user_id, unit_id)
);
CREATE INDEX idx_membership_unit ON membership (unit_id);

CREATE TABLE document
(
    id              UUID PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES org_unit (id),
    title           VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'EXTRACTED', 'FAILED')),
    failure_reason  VARCHAR(1000),
    fact_count      INTEGER      NOT NULL DEFAULT 0,
    rejected_count  INTEGER      NOT NULL DEFAULT 0,
    uploaded_by     BIGINT       NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_document_project ON document (project_id, created_at DESC);

-- Subject-predicate-object facts, each anchored to a character span of the source text.
CREATE TABLE fact
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id  UUID          NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    subject      VARCHAR(500)  NOT NULL,
    predicate    VARCHAR(500)  NOT NULL,
    object       VARCHAR(1000) NOT NULL,
    quote        TEXT          NOT NULL,
    start_offset INTEGER       NOT NULL,
    end_offset   INTEGER       NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_fact_document ON fact (document_id, start_offset);
