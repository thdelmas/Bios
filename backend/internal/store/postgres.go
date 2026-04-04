package store

import (
	"context"
	"fmt"

	"github.com/bios-health/backend/internal/model"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Store handles all database operations.
type Store struct {
	pool *pgxpool.Pool
}

// New creates a Store connected to the given PostgreSQL DSN.
func New(ctx context.Context, dsn string) (*Store, error) {
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		return nil, fmt.Errorf("connect to postgres: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, fmt.Errorf("ping postgres: %w", err)
	}
	return &Store{pool: pool}, nil
}

// Close releases the connection pool.
func (s *Store) Close() {
	s.pool.Close()
}

// Migrate creates tables if they don't exist.
func (s *Store) Migrate(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, schema)
	return err
}

const schema = `
CREATE TABLE IF NOT EXISTS users (
    id           TEXT PRIMARY KEY,
    public_key   BYTEA NOT NULL,
    device_ids   TEXT[] DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    community_tier BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS sync_payloads (
    id           BIGSERIAL PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id),
    device_id    TEXT NOT NULL,
    enc_payload  BYTEA NOT NULL,
    payload_hash TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, payload_hash)
);

CREATE INDEX IF NOT EXISTS idx_sync_payloads_user
    ON sync_payloads(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS aggregate_contributions (
    id           TEXT PRIMARY KEY,
    metric_type  TEXT NOT NULL,
    period       TEXT NOT NULL,
    mean         DOUBLE PRECISION NOT NULL,
    std_dev      DOUBLE PRECISION NOT NULL,
    sample_count INTEGER NOT NULL,
    age_group    TEXT,
    region       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_aggregate_metric
    ON aggregate_contributions(metric_type, period, created_at DESC);

CREATE TABLE IF NOT EXISTS population_signals (
    id          TEXT PRIMARY KEY,
    category    TEXT NOT NULL,
    severity    TEXT NOT NULL DEFAULT 'info',
    title       TEXT NOT NULL,
    description TEXT NOT NULL,
    region      TEXT NOT NULL DEFAULT '',
    timestamp   BIGINT NOT NULL,
    source      TEXT NOT NULL DEFAULT 'aggregate',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pop_signals_ts
    ON population_signals(timestamp DESC);

CREATE TABLE IF NOT EXISTS research_contributions (
    id         BIGSERIAL PRIMARY KEY,
    session_id TEXT NOT NULL,
    payload    BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS model_versions (
    version    INTEGER PRIMARY KEY,
    filename   TEXT NOT NULL,
    checksum   TEXT NOT NULL,
    signature  BYTEA NOT NULL,
    size_bytes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
`

// SaveSyncPayload stores an encrypted sync blob. Deduplicates by hash.
func (s *Store) SaveSyncPayload(ctx context.Context, p *model.SyncPayload) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO sync_payloads (user_id, device_id, enc_payload, payload_hash)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (user_id, payload_hash) DO NOTHING
	`, p.UserID, p.DeviceID, p.EncPayload, p.PayloadHash)
	return err
}

// GetSyncPayloads returns encrypted payloads for a user, newest first.
func (s *Store) GetSyncPayloads(ctx context.Context, userID string, limit int) ([]model.SyncPayload, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT user_id, device_id, enc_payload, payload_hash, created_at
		FROM sync_payloads
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var payloads []model.SyncPayload
	for rows.Next() {
		var p model.SyncPayload
		if err := rows.Scan(&p.UserID, &p.DeviceID, &p.EncPayload, &p.PayloadHash, &p.CreatedAt); err != nil {
			return nil, err
		}
		payloads = append(payloads, p)
	}
	return payloads, rows.Err()
}

// SaveContribution stores an anonymized aggregate contribution.
func (s *Store) SaveContribution(ctx context.Context, c *model.AggregateContribution) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO aggregate_contributions (id, metric_type, period, mean, std_dev, sample_count, age_group, region)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (id) DO NOTHING
	`, c.ID, c.MetricType, c.Period, c.Mean, c.StdDev, c.SampleCount, c.AgeGroup, c.Region)
	return err
}

// GetCommunityAggregates returns averaged community stats for a metric.
func (s *Store) GetCommunityAggregates(ctx context.Context, metricType, period string, limit int) ([]model.AggregateContribution, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, metric_type, period, mean, std_dev, sample_count, age_group, region, created_at
		FROM aggregate_contributions
		WHERE metric_type = $1 AND period = $2
		ORDER BY created_at DESC
		LIMIT $3
	`, metricType, period, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []model.AggregateContribution
	for rows.Next() {
		var c model.AggregateContribution
		if err := rows.Scan(&c.ID, &c.MetricType, &c.Period, &c.Mean, &c.StdDev, &c.SampleCount, &c.AgeGroup, &c.Region, &c.CreatedAt); err != nil {
			return nil, err
		}
		results = append(results, c)
	}
	return results, rows.Err()
}

// UpsertUser creates or updates a user record.
func (s *Store) UpsertUser(ctx context.Context, u *model.User) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO users (id, public_key, sync_enabled, community_tier)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (id) DO UPDATE SET
			last_seen_at = NOW(),
			sync_enabled = EXCLUDED.sync_enabled,
			community_tier = EXCLUDED.community_tier
	`, u.ID, u.PublicKey, u.SyncEnabled, u.CommunityTier)
	return err
}

// GetUser retrieves a user by ID.
func (s *Store) GetUser(ctx context.Context, id string) (*model.User, error) {
	row := s.pool.QueryRow(ctx, `
		SELECT id, public_key, device_ids, created_at, last_seen_at, sync_enabled, community_tier
		FROM users WHERE id = $1
	`, id)

	var u model.User
	if err := row.Scan(&u.ID, &u.PublicKey, &u.DeviceIDs, &u.CreatedAt, &u.LastSeenAt, &u.SyncEnabled, &u.CommunityTier); err != nil {
		return nil, err
	}
	return &u, nil
}

// DeleteUser removes a user and all associated data. Irreversible.
// Called when the owner requests "Delete all data" from the app.
func (s *Store) DeleteUser(ctx context.Context, userID string) error {
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	// Delete sync payloads (encrypted blobs — now irrecoverable without client key)
	if _, err := tx.Exec(ctx, `DELETE FROM sync_payloads WHERE user_id = $1`, userID); err != nil {
		return err
	}
	// Delete user record
	if _, err := tx.Exec(ctx, `DELETE FROM users WHERE id = $1`, userID); err != nil {
		return err
	}

	return tx.Commit(ctx)
}

// GetPopulationSignals returns active population health signals.
func (s *Store) GetPopulationSignals(ctx context.Context, limit int) ([]model.PopulationSignal, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, category, severity, title, description, region, timestamp, source
		FROM population_signals
		ORDER BY timestamp DESC
		LIMIT $1
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var signals []model.PopulationSignal
	for rows.Next() {
		var sig model.PopulationSignal
		if err := rows.Scan(&sig.ID, &sig.Category, &sig.Severity, &sig.Title, &sig.Description, &sig.Region, &sig.Timestamp, &sig.Source); err != nil {
			return nil, err
		}
		signals = append(signals, sig)
	}
	return signals, rows.Err()
}

// SaveResearchContribution stores a de-identified research contribution.
func (s *Store) SaveResearchContribution(ctx context.Context, c *model.ResearchContribution) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO research_contributions (session_id, payload)
		VALUES ($1, $2)
	`, c.SessionID, c.Payload)
	return err
}

// GetLatestModelVersion returns the newest model version metadata.
func (s *Store) GetLatestModelVersion(ctx context.Context) (*model.ModelVersion, error) {
	row := s.pool.QueryRow(ctx, `
		SELECT version, filename, checksum, signature, size_bytes, created_at
		FROM model_versions
		ORDER BY version DESC
		LIMIT 1
	`)

	var m model.ModelVersion
	if err := row.Scan(&m.Version, &m.Filename, &m.Checksum, &m.Signature, &m.SizeBytes, &m.CreatedAt); err != nil {
		return nil, err
	}
	return &m, nil
}
