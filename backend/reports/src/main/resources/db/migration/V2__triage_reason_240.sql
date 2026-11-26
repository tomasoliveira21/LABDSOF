-- US-34 (#229, spec D9): the model's reason has 1 to 240 characters (output schema of the evaluation package and
-- production contract item 2 of the local AI decisions); #194 had 200.
ALTER TABLE triage_suggestion ALTER COLUMN suggested_reason TYPE varchar(240);
