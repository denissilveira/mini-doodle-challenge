--liquibase formatted sql

-- Schema conventions:
--  * All timestamps use TIMESTAMPTZ and are persisted as UTC instants by default.
--  * gen_random_uuid() is available natively in PostgreSQL 13 and newer.

--changeset dsilveira:20260726-create-initial-schema
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE calendars (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    timezone   VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Slot statuses:
-- FREE - The slot is available.
-- BLOCKED - The user manually marked the interval as unavailable.
-- BOOKED - A meeting is associated with the slot.
CREATE TABLE slots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'FREE',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT slots_valid_range CHECK (end_at > start_at),
    CONSTRAINT slots_valid_status CHECK (status IN ('FREE', 'BLOCKED', 'BOOKED')),
    CONSTRAINT slots_unique_start UNIQUE (calendar_id, start_at)
);

CREATE INDEX idx_slots_calendar_range ON slots (calendar_id, start_at, end_at);

CREATE TABLE meetings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id     UUID NOT NULL UNIQUE REFERENCES slots(id) ON DELETE RESTRICT,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (meeting_id, user_id)
);

CREATE INDEX idx_meeting_participants_user_meeting
    ON meeting_participants (user_id, meeting_id);