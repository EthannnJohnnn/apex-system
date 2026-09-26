CREATE TABLE member (
    id UUID PRIMARY KEY,
    member_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    position VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('MEMBER', 'OFFICER', 'EXECUTIVE', 'PRESIDENT')),
    eligible BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (category <> 'PRESIDENT' OR eligible = FALSE)
);

CREATE TABLE member_eligibility_history (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member(id),
    eligible BOOLEAN NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    member_version BIGINT NOT NULL,
    UNIQUE (member_id, member_version)
);
