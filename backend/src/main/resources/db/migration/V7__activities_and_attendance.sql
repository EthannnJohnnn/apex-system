CREATE TABLE activity (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES academic_term(id),
    title VARCHAR(150) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('MEETING','EVENT')),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    location VARCHAR(200) NOT NULL DEFAULT '',
    description VARCHAR(2000) NOT NULL DEFAULT '',
    present_points INTEGER NOT NULL CHECK (present_points BETWEEN 0 AND 1000),
    late_points INTEGER NOT NULL CHECK (late_points BETWEEN 0 AND present_points),
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','FINALIZED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX activity_term_schedule ON activity(term_id, scheduled_at);
ALTER TABLE point_entry ADD COLUMN activity_id UUID REFERENCES activity(id);
CREATE TABLE attendance (
    activity_id UUID NOT NULL REFERENCES activity(id),
    member_id UUID NOT NULL REFERENCES member(id),
    status VARCHAR(10) CHECK (status IN ('PRESENT','LATE','EXCUSED','ABSENT')),
    eligible_snapshot BOOLEAN,
    points INTEGER NOT NULL DEFAULT 0 CHECK (points BETWEEN 0 AND 1000),
    entry_id UUID REFERENCES point_entry(id),
    PRIMARY KEY (activity_id, member_id)
);
CREATE TABLE activity_action (
    request_id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES activity(id),
    payload TEXT NOT NULL,
    actor VARCHAR(64) NOT NULL
);
CREATE TABLE attendance_history (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES activity(id),
    member_id UUID REFERENCES member(id),
    action VARCHAR(30) NOT NULL,
    old_status VARCHAR(10),
    new_status VARCHAR(10),
    old_points INTEGER,
    new_points INTEGER,
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activity_version BIGINT NOT NULL
);
