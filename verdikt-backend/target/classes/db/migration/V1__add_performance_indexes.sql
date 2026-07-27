-- ============================================================================
-- Verdikt — Performance Index Migration
-- Target: PostgreSQL 12+
-- Purpose: Eliminate sequential scans on hot paths (votes, room_questions,
--          players, rooms) identified during production-readiness audit.
--
-- Safety notes:
--   * IF NOT EXISTS makes this migration idempotent.
--   * No CONCURRENTLY — tables are small at migration time and Flyway runs
--     inside a transaction by default. If you ever hit table bloat, rebuild
--     these indexes manually with CONCURRENTLY during a maintenance window.
--   * All indexes use standard btree (PostgreSQL default) and NULLS FIRST for
--     booleans so partial-index patterns can be added later if needed.
-- ============================================================================

-- --------------------------------------------------------------------------
-- votes
-- --------------------------------------------------------------------------

-- Hot path: findAllByRoomIdAndQuestionId, findVotedForIdsByRoomAndQuestionAndVoter
-- These queries filter on (room_id, question_id) and are executed on every
-- vote cast, vote remove, and current-votes fetch.
CREATE INDEX IF NOT EXISTS idx_votes_room_question
    ON votes (room_id, question_id);

-- Hot path: deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn
-- Scans by voter to resolve a player's selections.
CREATE INDEX IF NOT EXISTS idx_votes_voter
    ON votes (voter_id);

-- Hot path: report-card aggregation (group by voted_for)
-- Counts votes received per player per question.
CREATE INDEX IF NOT EXISTS idx_votes_voted_for
    ON votes (voted_for_id);

-- --------------------------------------------------------------------------
-- room_questions
-- --------------------------------------------------------------------------

-- Hot path: findByRoomIdAndIsActiveTrue
-- Resolves the current active question for a room.
CREATE INDEX IF NOT EXISTS idx_room_questions_room_active
    ON room_questions (room_id, is_active);

-- Hot path: findAllByRoomIdOrderByOrderIndex
-- Resolves the ordered question list for game advancement.
CREATE INDEX IF NOT EXISTS idx_room_questions_room_order
    ON room_questions (room_id, order_index);

-- --------------------------------------------------------------------------
-- rooms
-- --------------------------------------------------------------------------

-- Hot path: expireStaleRooms (scheduled every 5 minutes)
-- Queries WAITING/IN_PROGRESS rooms where expires_at < now.
CREATE INDEX IF NOT EXISTS idx_rooms_status_expires
    ON rooms (status, expires_at);

-- --------------------------------------------------------------------------
-- players
-- --------------------------------------------------------------------------

-- Hot path: handleHostDisconnect, handleHostReconnect
-- Loads active players in a room to reassign host on disconnect.
CREATE INDEX IF NOT EXISTS idx_players_room_active
    ON players (room_id, is_active);

-- --------------------------------------------------------------------------
-- questions (bonus)
-- --------------------------------------------------------------------------

-- Hot path: findRandomQuestions (native ORDER BY id + random offset WHERE is_custom = false)
-- findByIsCustomFalse
-- Composite index lets PostgreSQL jump directly to a random offset without sorting.
CREATE INDEX IF NOT EXISTS idx_questions_is_custom_id
    ON questions (is_custom, id);

-- Keep the old simple index for queries that only filter by is_custom
CREATE INDEX IF NOT EXISTS idx_questions_is_custom
    ON questions (is_custom);
