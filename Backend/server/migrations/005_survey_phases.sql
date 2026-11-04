-- Hai đợt khảo sát bám sát phiếu nghiên cứu: PRE và POST.
ALTER TABLE pop_assessment_results
  ADD COLUMN IF NOT EXISTS phase TEXT NOT NULL DEFAULT 'PRE',
  ADD COLUMN IF NOT EXISTS profile JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS open_answers JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE pop_assessment_results
  DROP CONSTRAINT IF EXISTS pop_assessment_results_phase_check;
ALTER TABLE pop_assessment_results
  ADD CONSTRAINT pop_assessment_results_phase_check CHECK (phase IN ('PRE', 'POST'));
CREATE INDEX IF NOT EXISTS pop_assessment_phase_date_idx
  ON pop_assessment_results(phase, completed_at DESC);
