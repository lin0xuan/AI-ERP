-- Safe to repeat; does not delete chat history, source text or user-confirmed facts.
UPDATE ai_memory_item SET status='retired', embedding=NULL
WHERE kind='constraint' AND status='active';
