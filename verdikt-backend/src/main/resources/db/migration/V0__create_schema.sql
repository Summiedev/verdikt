-- Initial schema for fresh production databases.
-- Existing databases are preserved by the production Flyway baseline at V1.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    spice_level VARCHAR(32) NOT NULL,
    is_custom BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(6) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    vote_mode VARCHAR(32) NOT NULL,
    host_player_id UUID,
    max_players INTEGER NOT NULL DEFAULT 20,
    max_questions INTEGER,
    question_duration_seconds INTEGER,
    current_question_started_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    name VARCHAR(30) NOT NULL,
    token UUID NOT NULL UNIQUE,
    is_host BOOLEAN NOT NULL DEFAULT FALSE,
    is_original_host BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uq_players_room_name UNIQUE (room_id, name)
);

CREATE TABLE IF NOT EXISTS room_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    order_index INTEGER NOT NULL,
    is_answered BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_room_questions_room_question UNIQUE (room_id, question_id)
);

CREATE TABLE IF NOT EXISTS votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    voter_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    voted_for_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    cast_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_votes_selection UNIQUE (room_id, question_id, voter_id, voted_for_id)
);
