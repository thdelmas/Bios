package anon

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"time"
)

// FloorToWeek truncates a timestamp to the Monday 00:00:00 UTC of its
// ISO week. Removes sub-week temporal precision to prevent timing correlation.
func FloorToWeek(t time.Time) time.Time {
	weekday := t.Weekday()
	if weekday == time.Sunday {
		weekday = 7
	}
	monday := t.AddDate(0, 0, -int(weekday-time.Monday))
	return time.Date(monday.Year(), monday.Month(), monday.Day(), 0, 0, 0, 0, time.UTC)
}

// BracketSampleCount maps an exact sample count into a coarse bracket string.
// Removes uniqueness from small contributors while preserving analytic utility.
func BracketSampleCount(n int) string {
	switch {
	case n < 10:
		return "1-9"
	case n < 30:
		return "10-29"
	case n < 100:
		return "30-99"
	case n < 300:
		return "100-299"
	default:
		return "300+"
	}
}

// CoarsenRegion strips sub-country precision.
// "US-CA-SF" → "US", "DE" → "DE", "" → "".
func CoarsenRegion(region string) string {
	if i := strings.IndexByte(region, '-'); i >= 0 {
		return region[:i]
	}
	return region
}

// CoarsenAgeGroup maps fine-grained age brackets to wider decade bins.
// Already-coarse or unrecognized values pass through unchanged.
func CoarsenAgeGroup(ag string) string {
	if mapped, ok := ageGroupMap[ag]; ok {
		return mapped
	}
	return ag
}

var ageGroupMap = map[string]string{
	"18-19": "18-29", "20-24": "18-29", "25-29": "18-29",
	"30-34": "30-39", "35-39": "30-39",
	"40-44": "40-49", "45-49": "40-49",
	"50-54": "50-59", "55-59": "50-59",
	"60-64": "60-69", "65-69": "60-69",
	"70-74": "70+", "75-79": "70+", "80+": "70+",
}

// ContributorHash computes a one-way HMAC-SHA256 of a user ID, keyed with
// the server secret. Used to link contributions to a user for deletion
// (GDPR right to erasure) without storing the user ID in plaintext.
func ContributorHash(userID string, secret []byte) string {
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(userID))
	return hex.EncodeToString(mac.Sum(nil))
}

// CoarsenTimestamp truncates a timestamp to day granularity in UTC.
// Used for last_seen_at to prevent activity pattern inference.
func CoarsenTimestamp(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.UTC)
}
