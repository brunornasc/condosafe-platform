-- Garante extensões espaciais e UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Tabela de Condomínios e Geofence Seguro
CREATE TABLE condominiums (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              name VARCHAR(150) NOT NULL,
                              address TEXT NOT NULL,
                              safe_zone_center GEOMETRY(Point, 4326),
                              safe_zone_radius_meters DOUBLE PRECISION DEFAULT 100.0,
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Blocos
CREATE TABLE blocks (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        condominium_id UUID NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
                        name VARCHAR(50) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Unidades / Apartamentos
CREATE TABLE units (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       block_id UUID NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
                       unit_number VARCHAR(20) NOT NULL,
                       status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Usuários / Moradores
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       keycloak_id VARCHAR(100) UNIQUE NOT NULL,
                       unit_id UUID REFERENCES units(id),
                       full_name VARCHAR(150) NOT NULL,
                       email VARCHAR(150) UNIQUE NOT NULL,
                       role VARCHAR(50) NOT NULL,
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Logs Imutáveis de Acesso (Populada pelo Kafka Consumer)
CREATE TABLE access_logs (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             user_id UUID,
                             unit_id UUID,
                             gate_id VARCHAR(50) NOT NULL,
                             access_type VARCHAR(50) NOT NULL,
                             direction VARCHAR(10) NOT NULL, -- IN / OUT
                             granted BOOLEAN NOT NULL,
                             reason VARCHAR(100),
                             accessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices de Performance
CREATE INDEX idx_access_logs_user_id ON access_logs(user_id);
CREATE INDEX idx_access_logs_accessed_at ON access_logs(accessed_at);
CREATE INDEX idx_condominiums_safe_zone ON condominiums USING GIST(safe_zone_center);