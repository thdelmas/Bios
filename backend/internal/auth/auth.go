package auth

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
	"time"

	"golang.org/x/crypto/sha3"
)

// Token represents a bearer token with an embedded user ID and expiry.
// The token is AES-256-GCM encrypted so the user ID is not visible
// to anyone who intercepts the token.
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
// The payload (userID + expiry) is encrypted with AES-256-GCM so it
// appears as an opaque blob to anyone without the server secret.
func GenerateToken(userID string, ttl time.Duration, secret []byte) (string, error) {
	expiryUnix := time.Now().Add(ttl).Unix()
	plaintext := fmt.Sprintf("%s.%d", userID, expiryUnix)

	aesKey := deriveAESKey(secret)
	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return "", fmt.Errorf("create cipher: %w", err)
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("create GCM: %w", err)
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", fmt.Errorf("generate nonce: %w", err)
	}

	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.RawURLEncoding.EncodeToString(ciphertext), nil
}

// ValidateToken decrypts and validates a bearer token.
func ValidateToken(raw string, secret []byte) (*Token, error) {
	data, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return nil, ErrInvalidToken
	}

	aesKey := deriveAESKey(secret)
	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return nil, ErrInvalidToken
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, ErrInvalidToken
	}

	if len(data) < gcm.NonceSize() {
		return nil, ErrInvalidToken
	}

	nonce := data[:gcm.NonceSize()]
	ciphertext := data[gcm.NonceSize():]

	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, ErrInvalidToken
	}

	// plaintext format: "userID.expiryUnix"
	parts := strings.SplitN(string(plaintext), ".", 2)
	if len(parts) != 2 {
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

// deriveAESKey derives a 32-byte AES key from the server secret using SHA3-256.
func deriveAESKey(secret []byte) []byte {
	h := sha3.New256()
	h.Write([]byte("bios-token-key"))
	h.Write(secret)
	return h.Sum(nil)
}
