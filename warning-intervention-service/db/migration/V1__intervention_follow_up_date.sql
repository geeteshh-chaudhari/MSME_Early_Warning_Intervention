-- Run manually as the Service 3 schema owner after reviewing deployment requirements.
-- The original DDL is unchanged. Existing rows may retain NULL until rescheduled.
ALTER TABLE intervention ADD (follow_up_date DATE);
ALTER TABLE intervention ADD CONSTRAINT chk_intervention_followup_day
    CHECK (follow_up_date = TRUNC(follow_up_date));
CREATE INDEX idx_intervention_followup ON intervention (is_active, follow_up_date);
