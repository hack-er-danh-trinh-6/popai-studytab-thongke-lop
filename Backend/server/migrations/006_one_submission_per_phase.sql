-- Enforce one completed PRE and one completed POST survey per account.
-- Existing submissions are preserved; prior completions populate the phase claims.
CREATE TABLE IF NOT EXISTS pop_assessment_phase_claims (
  user_id UUID NOT NULL REFERENCES pop_users(id) ON DELETE CASCADE,
  phase TEXT NOT NULL CHECK (phase IN ('PRE', 'POST')),
  claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, phase)
);

INSERT INTO pop_assessment_phase_claims (user_id, phase, claimed_at)
SELECT user_id, phase, MIN(completed_at)
  FROM pop_assessment_results
 GROUP BY user_id, phase
ON CONFLICT (user_id, phase) DO NOTHING;
