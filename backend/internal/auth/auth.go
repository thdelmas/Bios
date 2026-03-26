package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
	"time"

	"golang.org/x/crypto/sha3"
)

// Token represents a bearer token with an embedded user ID and expiry.
// Format: userID.randomBytes.hmac
type Token struct {
	UserID    string
	ExpiresAt time.Time
	Raw       string
}

var (
	ErrInvalidToken = errors.New("invalid or expired token")
	ErrMissingAuth  = errors.New("missing authorization header")
)

// GenerateToken creates a new bearer token for the given user.
// Tokens expire after the specified duration.
func GenerateToken(userID string, ttl time.Duration, secret []byte) (string, error) {
	randomBytes := make([]byte, 32)
	if _, err := rand.Read(randomBytes); err != nil {
		return "", fmt.Errorf("generate random bytes: %w", err)
	}

	expiryUnix := time.Now().Add(ttl).Unix()
	payload := fmt.Sprintf("%s.%d.%s",
		userID,
		expiryUnix,
		base64.RawURLEncoding.EncodeToString(randomBytes),
	)

	mac := computeMAC(payload, secret)
	return payload + "." + mac, nil
}

// ValidateToken checks a bearer token and returns the embedded user ID.
func ValidateToken(raw string, secret []byte) (*Token, error) {
	parts := strings.Split(raw, ".")
	if len(parts) != 4 {
		return nil, ErrInvalidToken
	}

	payload := parts[0] + "." + parts[1] + "." + parts[2]
	expectedMAC := computeMAC(payload, secret)

	if subtle.ConstantTimeCompare([]byte(parts[3]), []byte(expectedMAC)) != 1 {
		return nil, ErrInvalidToken
	}

	var expiryUnix int64
	if _, err := fmt.Sscanf(parts[1], "%d", &expiryUnix); err != nil {
		return nil, ErrInvalidToken
	}

	expiresAt := time.Unix(expiryUnix, 0)
	if time.Now().After(expiresAt) {
		return nil, ErrInvalidToken
	}

	return &Token{
		UserID:    parts[0],
		ExpiresAt: expiresAt,
		Raw:       raw,
	}, nil
}

// ExtractBearer pulls the token from an "Authorization: Bearer <token>" header.
func ExtractBearer(header string) (string, error) {
	if header == "" {
		return "", ErrMissingAuth
	}
	parts := strings.SplitN(header, " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
		return "", ErrInvalidToken
	}
	return strings.TrimSpace(parts[1]), nil
}

func computeMAC(payload string, secret []byte) string {
	h := sha3.New256()
	h.Write([]byte(payload))
	h.Write(secret)
	return base64.RawURLEncoding.EncodeToString(h.Sum(nil))
}
