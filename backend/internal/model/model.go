package model

import "time"

// SyncPayload is the E2E-encrypted blob sent by the client.
// The server never sees plaintext health data — it stores
// the opaque encrypted payload keyed by the user's ID.
type SyncPayload struct {
	UserID      string    `json:"user_id"`
	DeviceID    string    `json:"device_id"`
	EncPayload  []byte    `json:"enc_payload"`  // AES-256-GCM encrypted
	PayloadHash string    `json:"payload_hash"` // SHA-256 for dedup
	CreatedAt   time.Time `json:"created_at"`
}

// AggregateContribution is an anonymized statistical summary
// sent by community-tier users. Contains no raw readings.
type AggregateContribution struct {
	ID          string    `json:"id"`
	MetricType  string    `json:"metric_type"`
	Period      string    `json:"period"`
	Mean        float64   `json:"mean"`
	StdDev      float64   `json:"std_dev"`
	SampleCount int       `json:"sample_count"`
	AgeGroup    string    `json:"age_group,omitempty"`
	Region      string    `json:"region,omitempty"`
	CreatedAt   time.Time `json:"created_at"`
}

// User holds authentication and key metadata. No health data.
type User struct {
	ID            string    `json:"id"`
	PublicKey     []byte    `json:"public_key"` // client's encryption public key
	DeviceIDs     []string  `json:"device_ids"`
	CreatedAt     time.Time `json:"created_at"`
	LastSeenAt    time.Time `json:"last_seen_at"`
	SyncEnabled   bool      `json:"sync_enabled"`
	CommunityTier bool      `json:"community_tier"`
}

// PopulationSignal is a population-level health pattern derived from
// anonymized Community contributions. No individual data.
type PopulationSignal struct {
	ID          string `json:"id"`
	Category    string `json:"category"`    // "respiratory", "gastrointestinal"
	Severity    string `json:"severity"`    // "info", "elevated", "high"
	Title       string `json:"title"`
	Description string `json:"description"`
	Region      string `json:"region"`      // broad region, never precise
	Timestamp   int64  `json:"timestamp"`
	Source      string `json:"source"`      // always "aggregate"
}

// ResearchContribution is a de-identified research data package.
// Separate consent from Community contributions.
type ResearchContribution struct {
	SessionID string `json:"session_id"` // random, not linked to account
	Payload   []byte `json:"payload"`    // de-identified JSON
	CreatedAt time.Time `json:"created_at"`
}

// ModelVersion tracks TFLite anomaly model releases.
type ModelVersion struct {
	Version   int    `json:"version"`
	Filename  string `json:"filename"`
	Checksum  string `json:"checksum"`  // SHA-256
	Signature []byte `json:"signature"` // Ed25519
	SizeBytes int    `json:"size_bytes"`
	CreatedAt time.Time `json:"created_at"`
}
