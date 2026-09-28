CREATE TABLE point_request (
    id UUID PRIMARY KEY,
    operation VARCHAR(20) NOT NULL,
    term_id UUID NOT NULL REFERENCES academic_term(id),
    member_id UUID NOT NULL REFERENCES member(id),
    source_id UUID,
    amount INTEGER NOT NULL CHECK (amount BETWEEN -1000 AND 1000),
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL
);
CREATE TABLE point_entry (
    id UUID PRIMARY KEY,
    sequence_no BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    request_id UUID NOT NULL REFERENCES point_request(id),
    term_id UUID NOT NULL REFERENCES academic_term(id),
    member_id UUID NOT NULL REFERENCES member(id),
    amount INTEGER NOT NULL CHECK (amount BETWEEN -1000 AND 1000),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('MANUAL','REVERSAL','REPLACEMENT')),
    reverses_id UUID UNIQUE REFERENCES point_entry(id),
    replaces_id UUID UNIQUE REFERENCES point_entry(id),
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((kind='MANUAL' AND reverses_id IS NULL AND replaces_id IS NULL AND amount <> 0)
        OR (kind='REVERSAL' AND reverses_id IS NOT NULL AND replaces_id IS NULL)
        OR (kind='REPLACEMENT' AND replaces_id IS NOT NULL AND reverses_id IS NULL))
);
CREATE INDEX point_entry_term_member ON point_entry(term_id, member_id, sequence_no);
