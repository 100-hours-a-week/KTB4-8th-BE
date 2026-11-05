-- Test-only extension: proves new nullable columns preserve existing rows.
ALTER TABLE video_analysis ADD COLUMN test_note VARCHAR(255) NULL;
