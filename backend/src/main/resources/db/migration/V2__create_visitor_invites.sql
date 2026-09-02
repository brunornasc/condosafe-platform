-- backend/src/main/resources/db/migration/V2__create_visitor_invites.sql

CREATE TABLE visitor_invites (
                                 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                 unit_id UUID NOT NULL REFERENCES units(id) ON DELETE CASCADE,
                                 resident_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 visitor_name VARCHAR(150) NOT NULL,
                                 document_number VARCHAR(30),
                                 valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
                                 valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
                                 max_uses INT NOT NULL DEFAULT 1,
                                 used_count INT NOT NULL DEFAULT 0,
                                 status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, REVOKED, COMPLETED
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_visitor_invites_unit ON visitor_invites(unit_id);
CREATE INDEX idx_visitor_invites_validity ON visitor_invites(valid_from, valid_until, status);