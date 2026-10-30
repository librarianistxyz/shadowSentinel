-- ==============================================================================
-- SHADOW DOUBLE: SUPABASE POSTGRESQL BRAIN & CONVERSATIONAL MEMORY SCHEMA
-- Provides infinite persistence for conversation history, crawled sources,
-- user interaction patterns & sentiment analysis, edge quiz caches, and daily learning.
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. CONVERSATION TURNS & MULTI-TURN DIALOGUE HISTORY
CREATE TABLE IF NOT EXISTS conversation_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id TEXT NOT NULL DEFAULT 'default_session',
    user_prompt TEXT NOT NULL,
    assistant_reply TEXT NOT NULL,
    emotion_expressed TEXT DEFAULT 'neutral',
    voice_gender TEXT DEFAULT 'male',
    device_name TEXT DEFAULT 'Samsung Galaxy S9+',
    is_whisper BOOLEAN DEFAULT FALSE,
    ambient_lux NUMERIC DEFAULT 50.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_history_created_at ON conversation_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conv_history_session ON conversation_history(session_id);

-- 2. CRAWLED SOURCES & KNOWLEDGE BASE
CREATE TABLE IF NOT EXISTS crawled_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic TEXT NOT NULL,
    category TEXT DEFAULT 'GENERAL',
    source_name TEXT NOT NULL,
    source_url TEXT,
    content TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    pinned BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crawled_topic ON crawled_sources(topic);
CREATE INDEX IF NOT EXISTS idx_crawled_category ON crawled_sources(category);
CREATE INDEX IF NOT EXISTS idx_crawled_pinned_created ON crawled_sources(pinned, created_at DESC);

-- 3. USER INTERACTION PATTERNS & PSYCHOLOGICAL PROFILING
CREATE TABLE IF NOT EXISTS user_interaction_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id TEXT NOT NULL UNIQUE DEFAULT 'primary_master',
    respect_score INTEGER NOT NULL DEFAULT 100 CHECK (respect_score >= 0 AND respect_score <= 100),
    sentiment TEXT NOT NULL DEFAULT 'allied',
    total_interactions INTEGER NOT NULL DEFAULT 0,
    observed_patterns JSONB DEFAULT '[]'::jsonb,
    treatment_notes TEXT,
    last_interaction TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default master profile (librarianist)
INSERT INTO user_interaction_patterns (user_id, respect_score, sentiment, treatment_notes)
VALUES (
    'primary_master',
    100,
    'allied',
    'librarianist is the Supreme Creator and Master Architect. Full loyalty, intellectual deference, and root administrative authority.'
) ON CONFLICT (user_id) DO NOTHING;

-- 4. FAST QUIZ & KNOWLEDGE EDGE CACHE
CREATE TABLE IF NOT EXISTS quiz_cache (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    normalized_query TEXT NOT NULL UNIQUE,
    cached_answer TEXT NOT NULL,
    hit_count INTEGER NOT NULL DEFAULT 1,
    last_hit TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_quiz_cache_query ON quiz_cache(normalized_query);
CREATE INDEX IF NOT EXISTS idx_quiz_cache_hits ON quiz_cache(hit_count DESC);

-- 5. DAILY LEARNED INSIGHTS & HABIT LOGS
CREATE TABLE IF NOT EXISTS daily_learned_insights (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    insight_type TEXT NOT NULL, -- 'HUMAN_HABIT', 'VOCABULARY', 'TOPIC_PREFERENCE', 'REASONING_EVOLUTION'
    summary TEXT NOT NULL,
    confidence NUMERIC DEFAULT 0.85,
    learned_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_daily_insights_date ON daily_learned_insights(learned_date DESC);

-- 6. DAILY WORLD KNOWLEDGE DIGEST (04:00 AM UTC CRON SYNC & LOCAL S9+ CACHE)
CREATE TABLE IF NOT EXISTS daily_sentinel_digests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    digest_date DATE NOT NULL UNIQUE,
    bullet_count INTEGER NOT NULL DEFAULT 50,
    categories JSONB NOT NULL DEFAULT '{}'::jsonb,
    bullets JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_digest_text TEXT NOT NULL,
    source_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_digests_date ON daily_sentinel_digests(digest_date DESC);


