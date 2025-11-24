-- Remove format column from union_graphs table
-- Format is no longer needed since chunks are always stored as Turtle
ALTER TABLE union_graphs DROP COLUMN IF EXISTS format;

