CREATE TABLE organization_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    organization_name VARCHAR(150) NOT NULL,
    meeting_present INTEGER NOT NULL DEFAULT 2 CHECK (meeting_present BETWEEN 0 AND 1000),
    meeting_late INTEGER NOT NULL DEFAULT 1 CHECK (meeting_late BETWEEN 0 AND 1000),
    event_present INTEGER NOT NULL DEFAULT 3 CHECK (event_present BETWEEN 0 AND 1000),
    event_late INTEGER NOT NULL DEFAULT 2 CHECK (event_late BETWEEN 0 AND 1000),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (meeting_late <= meeting_present AND event_late <= event_present)
);
INSERT INTO organization_settings (id, organization_name) VALUES (1, 'PSIM - SLSU SU');

CREATE TABLE academic_term (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_key VARCHAR(100) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    active_slot INTEGER UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (end_date >= start_date),
    CHECK ((status = 'ACTIVE' AND active_slot IS NOT NULL AND active_slot = 1) OR (status <> 'ACTIVE' AND active_slot IS NULL))
);

CREATE TABLE term_history (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES academic_term(id),
    action VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    term_version BIGINT NOT NULL,
    UNIQUE (term_id, term_version)
);
