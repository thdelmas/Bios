package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Byte-format parser for the Bluetooth SIG **Environmental Sensing
 * Service** (ESS, service UUID `0x181A`). The runtime BLE adapter
 * (deferred to phase 2) hands raw characteristic payloads to these pure
 * functions; the values come back as canonical [MetricReading]s on
 * Bios's bus.
 *
 * Three characteristics are supported, matching the three [MetricType]s
 * #43 introduces:
 *
 *  - **PM2.5** (`0x2BD4`, "Particulate Matter PM2.5 Concentration") —
 *    `uint16`, resolution **0.1 µg/m³**. Range 0.0–6553.5 µg/m³.
 *  - **CO2** (`0x2B8C`, "CO2 Concentration") — `uint16`, **ppm**,
 *    integer resolution. Range 0–65 535 ppm.
 *  - **VOC** (`0x2BE7`, "VOC Concentration") — `uint16`, **ppb**,
 *    integer resolution. Range 0–65 535 ppb.
 *
 * All three characteristics use little-endian byte order (Bluetooth
 * convention). The "not known" sentinel `0xFFFF` from the spec is
 * surfaced as `null` rather than a 6553.5 / 65 535 reading — it would
 * pollute baselines if treated as data.
 *
 * Lives next to the existing adapters in `ingest/` so its callers (the
 * future `BleAirQualityAdapter` runtime) can wire it without a
 * cross-package dependency, but is fully exercise-able in unit tests
 * without any Android Bluetooth runtime.
 */
object EnvironmentalSensingParser {

    /** Bluetooth SIG GATT characteristic UUID for PM2.5 (16-bit form). */
    const val CHARACTERISTIC_PM25_SHORT = 0x2BD4
    /** Bluetooth SIG GATT characteristic UUID for CO2 (16-bit form). */
    const val CHARACTERISTIC_CO2_SHORT = 0x2B8C
    /** Bluetooth SIG GATT characteristic UUID for VOC (16-bit form). */
    const val CHARACTERISTIC_VOC_SHORT = 0x2BE7

    /** Bluetooth-spec "value not known" sentinel for uint16 characteristics. */
    private const val NOT_KNOWN_UINT16: Int = 0xFFFF

    /**
     * Parse the PM2.5 characteristic payload. The spec stores
     * concentration as `uint16` with a 0.1 µg/m³ resolution — the raw
     * value is the concentration multiplied by 10 — so the result is
     * `raw / 10.0` µg/m³.
     *
     * Returns `null` for the "not known" sentinel, for byte arrays
     * shorter than 2 bytes, or when the converted value would be
     * negative (defensive — the underlying type is unsigned, but a
     * caller-supplied JVM `ByteArray` is signed and a malicious /
     * malformed payload could produce a negative intermediate).
     */
    fun parsePm25(bytes: ByteArray, timestamp: Long, sourceId: String): MetricReading? {
        val raw = readUint16Le(bytes) ?: return null
        if (raw == NOT_KNOWN_UINT16) return null
        val ugPerM3 = raw / 10.0
        if (ugPerM3 < 0.0) return null
        return MetricReading(
            metricType = MetricType.AIR_PM25.key,
            value = ugPerM3,
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    /**
     * Parse the CO2 characteristic payload. `uint16`, integer ppm.
     * Returns `null` for the spec sentinel or short payloads.
     */
    fun parseCo2(bytes: ByteArray, timestamp: Long, sourceId: String): MetricReading? {
        val raw = readUint16Le(bytes) ?: return null
        if (raw == NOT_KNOWN_UINT16) return null
        return MetricReading(
            metricType = MetricType.AIR_CO2.key,
            value = raw.toDouble(),
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    /**
     * Parse the VOC characteristic payload. `uint16`, integer ppb.
     * Returns `null` for the spec sentinel or short payloads.
     */
    fun parseVoc(bytes: ByteArray, timestamp: Long, sourceId: String): MetricReading? {
        val raw = readUint16Le(bytes) ?: return null
        if (raw == NOT_KNOWN_UINT16) return null
        return MetricReading(
            metricType = MetricType.AIR_VOC.key,
            value = raw.toDouble(),
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    /**
     * Dispatch a raw GATT notification to the correct parser by the
     * characteristic's 16-bit short UUID. Returns `null` when the UUID
     * isn't one Bios supports — the caller (runtime BLE adapter) logs
     * and moves on rather than crashing.
     */
    fun parse(
        characteristicShortUuid: Int,
        bytes: ByteArray,
        timestamp: Long,
        sourceId: String,
    ): MetricReading? = when (characteristicShortUuid) {
        CHARACTERISTIC_PM25_SHORT -> parsePm25(bytes, timestamp, sourceId)
        CHARACTERISTIC_CO2_SHORT -> parseCo2(bytes, timestamp, sourceId)
        CHARACTERISTIC_VOC_SHORT -> parseVoc(bytes, timestamp, sourceId)
        else -> null
    }

    /**
     * Little-endian uint16 read out of a [ByteArray]. Returns `null`
     * when the array has fewer than 2 bytes — defensive against
     * truncated GATT notifications. Bytes after the first two are
     * ignored; some ESS characteristics carry trailing flags / extra
     * fields the air-quality keys don't use.
     */
    private fun readUint16Le(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        val low = bytes[0].toInt() and 0xFF
        val high = bytes[1].toInt() and 0xFF
        return (high shl 8) or low
    }
}
