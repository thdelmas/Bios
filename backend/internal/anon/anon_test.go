package anon

import (
	"testing"
	"time"
)

func TestFloorToWeek(t *testing.T) {
	tests := []struct {
		name string
		in   time.Time
		want time.Time
	}{
		{
			name: "sunday floors to previous monday",
			in:   time.Date(2026, 4, 12, 15, 30, 0, 0, time.UTC), // Sunday
			want: time.Date(2026, 4, 6, 0, 0, 0, 0, time.UTC),   // Monday
		},
		{
			name: "monday stays on monday",
			in:   time.Date(2026, 4, 6, 9, 0, 0, 0, time.UTC),
			want: time.Date(2026, 4, 6, 0, 0, 0, 0, time.UTC),
		},
		{
			name: "wednesday floors to monday",
			in:   time.Date(2026, 4, 8, 14, 22, 33, 0, time.UTC),
			want: time.Date(2026, 4, 6, 0, 0, 0, 0, time.UTC),
		},
		{
			name: "saturday floors to monday",
			in:   time.Date(2026, 4, 11, 23, 59, 59, 0, time.UTC),
			want: time.Date(2026, 4, 6, 0, 0, 0, 0, time.UTC),
		},
		{
			name: "strips time component",
			in:   time.Date(2026, 1, 5, 12, 30, 45, 123, time.UTC), // Monday
			want: time.Date(2026, 1, 5, 0, 0, 0, 0, time.UTC),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := FloorToWeek(tt.in)
			if !got.Equal(tt.want) {
				t.Errorf("FloorToWeek(%v) = %v, want %v", tt.in, got, tt.want)
			}
		})
	}
}

func TestBracketSampleCount(t *testing.T) {
	tests := []struct {
		n    int
		want string
	}{
		{1, "1-9"}, {9, "1-9"},
		{10, "10-29"}, {29, "10-29"},
		{30, "30-99"}, {99, "30-99"},
		{100, "100-299"}, {299, "100-299"},
		{300, "300+"}, {1000, "300+"},
	}

	for _, tt := range tests {
		got := BracketSampleCount(tt.n)
		if got != tt.want {
			t.Errorf("BracketSampleCount(%d) = %q, want %q", tt.n, got, tt.want)
		}
	}
}

func TestCoarsenRegion(t *testing.T) {
	tests := []struct {
		in, want string
	}{
		{"US-CA-SF", "US"},
		{"US-CA", "US"},
		{"DE", "DE"},
		{"", ""},
		{"FR-IDF", "FR"},
	}

	for _, tt := range tests {
		got := CoarsenRegion(tt.in)
		if got != tt.want {
			t.Errorf("CoarsenRegion(%q) = %q, want %q", tt.in, got, tt.want)
		}
	}
}

func TestCoarsenAgeGroup(t *testing.T) {
	tests := []struct {
		in, want string
	}{
		{"18-19", "18-29"}, {"20-24", "18-29"}, {"25-29", "18-29"},
		{"30-34", "30-39"}, {"35-39", "30-39"},
		{"40-44", "40-49"}, {"45-49", "40-49"},
		{"50-54", "50-59"}, {"55-59", "50-59"},
		{"60-64", "60-69"}, {"65-69", "60-69"},
		{"70-74", "70+"}, {"75-79", "70+"}, {"80+", "70+"},
		// Already coarse or unknown pass through
		{"30-39", "30-39"},
		{"unknown", "unknown"},
		{"", ""},
	}

	for _, tt := range tests {
		got := CoarsenAgeGroup(tt.in)
		if got != tt.want {
			t.Errorf("CoarsenAgeGroup(%q) = %q, want %q", tt.in, got, tt.want)
		}
	}
}

func TestContributorHash(t *testing.T) {
	secret := []byte("test-secret-32-bytes-long-ok!!!!!")

	// Deterministic: same input produces same output
	h1 := ContributorHash("user-123", secret)
	h2 := ContributorHash("user-123", secret)
	if h1 != h2 {
		t.Error("ContributorHash should be deterministic")
	}

	// Different users produce different hashes
	h3 := ContributorHash("user-456", secret)
	if h1 == h3 {
		t.Error("different users should produce different hashes")
	}

	// Different secrets produce different hashes
	h4 := ContributorHash("user-123", []byte("different-secret"))
	if h1 == h4 {
		t.Error("different secrets should produce different hashes")
	}

	// Output is hex-encoded SHA-256 (64 chars)
	if len(h1) != 64 {
		t.Errorf("hash length = %d, want 64", len(h1))
	}
}

func TestCoarsenTimestamp(t *testing.T) {
	in := time.Date(2026, 4, 12, 15, 30, 45, 123, time.UTC)
	want := time.Date(2026, 4, 12, 0, 0, 0, 0, time.UTC)
	got := CoarsenTimestamp(in)
	if !got.Equal(want) {
		t.Errorf("CoarsenTimestamp(%v) = %v, want %v", in, got, want)
	}
}
