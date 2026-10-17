-- V5: FULLTEXT index on events for public search.
--
-- FULLTEXT rather than LIKE because LIKE '%keyword%' can't use a B-tree index (the
-- leading wildcard blocks range scans) and scans the whole table. A FULLTEXT index
-- keeps an inverted word-to-row map, so MATCH(title, description) AGAINST(...)
-- resolves against the index instead of scanning every row.
--
-- The composite index on (status, start_datetime) covers the common browse query
-- (WHERE status = 'PUBLISHED' ORDER BY start_datetime ASC), so MySQL does one index
-- range scan instead of a full scan plus filesort.

ALTER TABLE events
    ADD FULLTEXT INDEX ft_events_title_desc (title, description);

CREATE INDEX idx_events_status_start
    ON events (status, start_datetime);
