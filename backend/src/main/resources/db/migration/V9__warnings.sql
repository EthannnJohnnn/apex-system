CREATE TABLE warning_record (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES academic_term(id),
    member_id UUID NOT NULL REFERENCES member(id),
    incident_key VARCHAR(200) NOT NULL,
    incident VARCHAR(180) NOT NULL,
    severity VARCHAR(10) CHECK (severity IN ('MINOR','MAJOR')),
    activity_id UUID REFERENCES activity(id),
    assigned_role VARCHAR(150) NOT NULL DEFAULT '',
    deduction INTEGER NOT NULL CHECK (deduction BETWEEN 0 AND 1000),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN','RESOLVED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(term_id,member_id,incident_key),
    CHECK (severity IS NOT NULL OR deduction > 0)
);
ALTER TABLE point_entry ADD COLUMN warning_id UUID REFERENCES warning_record(id);
CREATE TABLE warning_action (
    request_id UUID PRIMARY KEY,
    warning_id UUID NOT NULL REFERENCES warning_record(id),
    payload TEXT NOT NULL,
    actor VARCHAR(64) NOT NULL
);
CREATE TABLE warning_history (
    id UUID PRIMARY KEY,
    warning_id UUID NOT NULL REFERENCES warning_record(id),
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL,
    UNIQUE(warning_id,version)
);
CREATE INDEX warning_term_member ON warning_record(term_id,member_id);
