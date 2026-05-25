# Makefile for Bios
# Run with BUILD=prod for release build, or BUILD=debug (default) for debug.

APP_ID := com.bios.app/.ui.MainActivity
GRADLEW := cd android && ./gradlew

# Build variant: debug (default) or prod (release)
# Device IDs
SAMSUNG_ID := 616ecbcf
PIXEL4A_ID := 0B201JECB13875
PIXEL9A_ID := 59101JEBF02652

# Where pre-install DB snapshots live. Each install pulls the encrypted
# bios.db + WAL + SHM from the device and stores them here, named with
# a UTC timestamp and the device model+serial. Restore is just a copy
# back via `adb push` + `run-as cp`; see scripts/restore-db.md.
DB_BACKUP_DIR := db-backups

BUILD ?= debug
FLAVOR ?= standalone
ifeq ($(BUILD),prod)
  GRADLE_VARIANT := $(shell echo $(FLAVOR) | sed 's/./\U&/')Release
else
  BUILD := debug
  GRADLE_VARIANT := $(shell echo $(FLAVOR) | sed 's/./\U&/')Debug
endif

.PHONY: all
all: help

# === Build ===

.PHONY: assemble
assemble:
	$(GRADLEW) assemble$(GRADLE_VARIANT)

.PHONY: install
install: db-backup
	@if adb devices | grep -q 'device$$'; then \
		$(GRADLEW) install$(GRADLE_VARIANT); \
	else \
		echo "No device connected. Building APK only."; \
		$(GRADLEW) assemble$(GRADLE_VARIANT); \
	fi

# Pull bios.db (+ WAL + SHM) off the connected device into
# $(DB_BACKUP_DIR) before any install. Filename embeds a UTC timestamp,
# the device's ro.product.model, and its ADB serial — so a multi-device
# workflow doesn't collide and a restore can pick the right snapshot
# without ambiguity. Skips cleanly when no device is connected or Bios
# isn't installed yet (fresh device case), so it never blocks a build.
.PHONY: db-backup
db-backup:
	@mkdir -p $(DB_BACKUP_DIR)
	@if ! adb get-state >/dev/null 2>&1; then \
		echo "[db-backup] no device — skipping backup."; \
		exit 0; \
	fi; \
	if ! adb shell pm path com.bios.app >/dev/null 2>&1; then \
		echo "[db-backup] com.bios.app not installed — skipping backup."; \
		exit 0; \
	fi; \
	TS=$$(date -u +%Y%m%dT%H%M%SZ); \
	SERIAL=$$(adb get-serialno | tr -d '\r\n'); \
	MODEL=$$(adb shell getprop ro.product.model | tr -d '\r\n' | tr ' ' '_'); \
	OUT_BASE=$(DB_BACKUP_DIR)/bios-$${TS}-$${MODEL}-$${SERIAL}; \
	echo "[db-backup] $${MODEL} ($${SERIAL}) → $${OUT_BASE}.db"; \
	if ! adb exec-out run-as com.bios.app cat databases/bios.db > "$${OUT_BASE}.db" 2>/dev/null || [ ! -s "$${OUT_BASE}.db" ]; then \
		echo "[db-backup] bios.db not readable (debug build only) or empty — skipping."; \
		rm -f "$${OUT_BASE}.db"; \
		exit 0; \
	fi; \
	adb exec-out run-as com.bios.app cat databases/bios.db-wal > "$${OUT_BASE}.db-wal" 2>/dev/null; \
	[ -s "$${OUT_BASE}.db-wal" ] || rm -f "$${OUT_BASE}.db-wal"; \
	adb exec-out run-as com.bios.app cat databases/bios.db-shm > "$${OUT_BASE}.db-shm" 2>/dev/null; \
	[ -s "$${OUT_BASE}.db-shm" ] || rm -f "$${OUT_BASE}.db-shm"; \
	SIZE=$$(stat -c %s "$${OUT_BASE}.db" 2>/dev/null || stat -f %z "$${OUT_BASE}.db"); \
	echo "[db-backup] ok ($${SIZE} bytes)"

.PHONY: check
check:
	$(GRADLEW) check

.PHONY: lint
lint:
	$(GRADLEW) lint$(GRADLE_VARIANT)

.PHONY: test
test:
	$(GRADLEW) test$(GRADLE_VARIANT)UnitTest

.PHONY: clean
clean:
	$(GRADLEW) clean

# === Device ===

.PHONY: devices
devices:
	adb devices

.PHONY: run
run: install
	@adb shell am start -n $(APP_ID)

.PHONY: run-pixel4a
run-pixel4a: install
	adb -s $(PIXEL4A_ID) shell am start -n $(APP_ID)

.PHONY: run-pixel9a
run-pixel9a: install
	adb -s $(PIXEL9A_ID) shell am start -n $(APP_ID)

.PHONY: run-samsung
run-samsung: install
	adb -s $(SAMSUNG_ID) shell am start -n $(APP_ID)

.PHONY: logs
logs:
	@trap 'cd android && ./gradlew --stop' EXIT INT TERM; adb logcat --pid=$$(adb shell pidof com.bios.app)

.PHONY: clear-data
clear-data:
	adb shell pm clear com.bios.app

# === Maintenance ===

.PHONY: clean-daemons
clean-daemons:
	$(GRADLEW) --stop

# === Help ===

.PHONY: help
help:
	@echo "Bios"
	@echo ""
	@echo "Build:"
	@echo "  make assemble       - Build APK (no device needed). BUILD=prod for release."
	@echo "  make install        - Backup DB + build + install on connected device. BUILD=prod for release."
	@echo "  make db-backup      - Snapshot bios.db (timestamped + device-identified) to $(DB_BACKUP_DIR)/."
	@echo "  make check          - Run all Gradle checks (lint + tests)."
	@echo "  make lint           - Run Android lint."
	@echo "  make test           - Run unit tests."
	@echo "  make clean          - Clean build outputs."
	@echo ""
	@echo "Device:"
	@echo "  make run            - Install and launch on connected device."
	@echo "  make run-pixel4a    - Install and run on Pixel 4a (ID: $(PIXEL4A_ID))."
	@echo "  make run-pixel9a    - Install and run on Pixel 9a (ID: $(PIXEL9A_ID))."
	@echo "  make run-samsung    - Install and run on Samsung (ID: $(SAMSUNG_ID))."
	@echo "  make devices        - List connected ADB devices."
	@echo "  make logs           - Tail app logs (Ctrl+C to stop)."
	@echo "  make clear-data     - Wipe app data on device."
	@echo ""
	@echo "Maintenance:"
	@echo "  make clean-daemons  - Stop idle Gradle daemons (frees ~4GB each)."
	@echo ""
	@echo "Build variant: BUILD=debug (default) or BUILD=prod"
	@echo "Build flavor:  FLAVOR=standalone (default) or FLAVOR=lethe"
