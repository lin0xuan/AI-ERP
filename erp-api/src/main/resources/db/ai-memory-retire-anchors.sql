-- The former spill path promoted session-scoped text without consent or conflict handling.
-- Retain these rows for inspection but never use them as active long-term facts again.
-- The session transcript remains intact and supplies verbatim constraints after upgrading.
UPDATE ai_memory_item SET status='retired', embedding=NULL
WHERE kind='constraint' AND status='active';
