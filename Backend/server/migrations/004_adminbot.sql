ALTER TABLE pop_users
    ADD COLUMN IF NOT EXISTS is_synthetic BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pop_focus_sessions
    ADD COLUMN IF NOT EXISTS is_synthetic BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS pop_focus_synthetic_date_idx
    ON pop_focus_sessions(created_at DESC) WHERE is_synthetic = TRUE;

CREATE TABLE IF NOT EXISTS pop_adminbot_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    teacher_id UUID NOT NULL REFERENCES pop_users(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES pop_classes(id) ON DELETE CASCADE,
    is_running BOOLEAN NOT NULL DEFAULT FALSE,
    seeded_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    last_generated_at TIMESTAMPTZ,
    generated_sessions INTEGER NOT NULL DEFAULT 0 CHECK (generated_sessions >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
