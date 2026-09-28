package com.bios.app.ingest

import android.util.Log
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.bios.app.model.SourceType

/**
 * Pulls nightly sleep HRV and resting heart rate from the COROS Training Hub
 * web API — the unofficial one the COROS web dashboard itself talks to.
 *
 * Why this exists: the COROS phone app writes sleep sessions, heart rate and
 * steps to Health Connect but never HRV or resting HR (verified 2026-09-15
 * with a Health Connect probe: 0 HRV records against 6 COROS sleep sessions
 * in the same window). W2F's HRV channel had been dark since the watch was
 * bought. Sleep and steps are deliberately NOT emitted here — Health Connect
 * already carries them from the same watch, and a second copy would be the
 * double-count the source arbiter was built to stop.
 *
 * Auth: `POST /account/login` with the account e-mail and an MD5 of the
 * password (that is what the vendor's own web client sends). Only the
 * returned `accessToken` is kept, in [ApiTokenStore]; the password never
 * touches storage. Data: `GET /analyse/dayDetail/query?startDay&endDay`
 * (yyyyMMdd, inclusive) → `data.dayList[]` with `happenDay`, `avgSleepHrv`,
 * `rhr`. Each value belongs to the sleep that ended on `happenDay`, so the
 * reading is stamped at 06:00 local on that day.
 *
 * Failure visibility: the last outcome ("OK …" / "ERR …") is written to the
 * token store under [STATUS_KEY] and shown in the connect dialog, and every
 * refusal is also reported through [recordHealth] to the
 * [SourceHealthStore], where the settings row, the disconnect push and the
 * provider's `/sources` read it (docs/specs/source-liveness.md). A session
 * that dies reads as "needs attention", never as silence.
 *
 * Re-login: when the owner opted to keep the account password
 * ([EMAIL_KEY] / [PASSWORD_KEY] in the encrypted store), a token refusal
 * triggers one login and one retry before the refusal is reported.
 */
class CorosApiAdapter(
    private val getToken: () -> String?,
    private val hasToken: () -> Boolean,
    private val getRegion: () -> String,
    private val recordStatus: (String) -> Unit = {},
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val getCredentials: () -> Pair<String, String>? = { null },
    private val saveToken: (String) -> Unit = {},
    private val recordHealth: (ok: Boolean, message: String?, action: OwnerAction) -> Unit = { _, _, _ -> },
) {
    constructor(tokenStore: ApiTokenStore, healthStore: SourceHealthStore? = null) : this(
        getToken = { tokenStore.getToken(PROVIDER_KEY) },
        hasToken = { tokenStore.hasToken(PROVIDER_KEY) },
        getRegion = { tokenStore.getToken(REGION_KEY) ?: DEFAULT_REGION },
        recordStatus = { tokenStore.saveToken(STATUS_KEY, it) },
        getCredentials = {
            val email = tokenStore.getToken(EMAIL_KEY)
            val password = tokenStore.getToken(PASSWORD_KEY)
            if (email != null && password != null) email to password else null
        },
        saveToken = { tokenStore.saveToken(PROVIDER_KEY, it) },
        recordHealth = { ok, message, action ->
            if (healthStore != null) {
                if (ok) healthStore.recordOk(SourceType.COROS_API)
                else healthStore.recordError(SourceType.COROS_API, message ?: "unknown error", action)
            }
        },
    )

    private val client = defaultApiClient()

    val isConnected: Boolean get() = hasToken()

    /** Session token exchange. Returns the token; the caller stores it. */
    suspend fun login(email: String, password: String, region: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = BASE_URLS[region] ?: error("Unknown COROS region '$region'")
                val body = JSONObject()
                    .put("account", email.trim())
                    .put("accountType", 2)
                    .put("pwd", md5Hex(password))
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder().url("$base/account/login").post(body).build()
                val json = client.getJson(request) ?: error("COROS login: empty or non-2xx response")
                val result = json.optString("result")
                if (result != RESULT_OK) {
                    error("COROS login refused ($result): ${json.optString("message")}")
                }
                val token = json.optJSONObject("data")?.optString("accessToken").orEmpty()
                if (token.isBlank()) error("COROS login: no accessToken in response")
                recordStatus("Connected ${LocalDate.now(zone)} ($region)")
                token
            }.onFailure { recordStatus("ERR login: ${it.message}") }
        }

    suspend fun fetchReadings(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> = withContext(Dispatchers.IO) {
        var token = getToken() ?: return@withContext emptyList()
        val region = getRegion()
        val base = BASE_URLS[region] ?: run {
            recordStatus("ERR unknown region '$region'")
            recordHealth(false, "unknown region '$region'", OwnerAction.REAUTH)
            return@withContext emptyList()
        }
        val startDay = startTime.atZone(zone).toLocalDate()
        val endDay = endTime.atZone(zone).toLocalDate()
        val url = "$base/analyse/dayDetail/query" +
            "?startDay=${DAY_FORMAT.format(startDay)}&endDay=${DAY_FORMAT.format(endDay)}"

        var relogged = false
        while (true) {
            val json = when (val outcome = requestDayDetail(url, token)) {
                is DayDetailOutcome.Body -> outcome.json
                is DayDetailOutcome.Failed -> return@withContext emptyList()
            }
            val result = json.optString("result")
            if (result == RESULT_OK) {
                val readings = parseDayList(json, sourceId, zone)
                recordStatus("OK ${LocalDate.now(zone)}: ${readings.size} readings for $startDay..$endDay")
                recordHealth(true, null, OwnerAction.NONE)
                return@withContext readings
            }
            val message = json.optString("message")
            val action = ownerActionFor(result, message)
            if (action == OwnerAction.REAUTH && !relogged) {
                val creds = getCredentials()
                if (creds != null) {
                    relogged = true
                    val fresh = login(creds.first, creds.second, region).getOrNull()
                    if (fresh != null) {
                        saveToken(fresh)
                        token = fresh
                        Log.i(TAG, "dayDetail refused ($result); re-logged in from stored credentials")
                        continue
                    }
                }
            }
            recordStatus("ERR dayDetail ($result): $message")
            recordHealth(false, "$message ($result)", action)
            Log.w(TAG, "dayDetail refused: $result $message")
            return@withContext emptyList()
        }
        @Suppress("UNREACHABLE_CODE")
        emptyList<MetricReading>()
    }

    private sealed class DayDetailOutcome {
        data class Body(val json: JSONObject) : DayDetailOutcome()
        object Failed : DayDetailOutcome()
    }

    /** One HTTP round-trip. Transport failures are recorded without an owner action. */
    private fun requestDayDetail(url: String, token: String): DayDetailOutcome {
        val request = Request.Builder().url(url).header(TOKEN_HEADER, token).get().build()
        val json = try {
            client.getJson(request)
        } catch (e: IOException) {
            recordStatus("ERR dayDetail: ${e.message}")
            recordHealth(false, "network: ${e.message}", OwnerAction.NONE)
            Log.w(TAG, "dayDetail request failed", e)
            return DayDetailOutcome.Failed
        } catch (e: JSONException) {
            recordStatus("ERR dayDetail: malformed body (${e.message})")
            recordHealth(false, "malformed body", OwnerAction.NONE)
            Log.w(TAG, "dayDetail body unparseable", e)
            return DayDetailOutcome.Failed
        }
        if (json == null) {
            recordStatus("ERR dayDetail: empty or non-2xx response")
            recordHealth(false, "empty or non-2xx response", OwnerAction.NONE)
            return DayDetailOutcome.Failed
        }
        return DayDetailOutcome.Body(json)
    }

    companion object {
        private const val TAG = "Bios.Coros"
        const val PROVIDER_KEY = "coros"
        const val REGION_KEY = "coros_region"
        const val STATUS_KEY = "coros_status"
        /** Set only when the owner chose to keep the password for automatic re-login. */
        const val EMAIL_KEY = "coros_email"
        const val PASSWORD_KEY = "coros_password"
        /** Vendor result code for a dead or rejected session token. */
        const val RESULT_TOKEN_INVALID = "1019"
        const val DEFAULT_REGION = "eu"
        const val TOKEN_HEADER = "accessToken"
        const val RESULT_OK = "0000"
        val BASE_URLS: Map<String, String> = mapOf(
            "eu" to "https://teameuapi.coros.com",
            "us" to "https://teamapi.coros.com",
            "cn" to "https://teamcnapi.coros.com"
        )
        val REGION_LABELS: Map<String, String> = mapOf(
            "eu" to "Europe", "us" to "Americas / rest of world", "cn" to "China / Asia"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        /** The reading is stamped at this local time on its wake day. */
        val READING_TIME: LocalTime = LocalTime.of(6, 0)

        /**
         * What the owner can do about a refusal. A dead session is theirs to
         * fix by signing in again; any other refusal has no owner action and
         * surfaces through staleness if it persists.
         */
        fun ownerActionFor(result: String, message: String?): OwnerAction =
            if (result == RESULT_TOKEN_INVALID || message?.contains("token", ignoreCase = true) == true) {
                OwnerAction.REAUTH
            } else {
                OwnerAction.NONE
            }

        fun md5Hex(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /**
         * Deterministic id per (metric, wake day): the same night re-fetched
         * by the next sync REPLACEs its own row instead of adding a twin.
         */
        fun readingId(metricType: String, happenDay: Int): String =
            "coros:$metricType:$happenDay"

        /**
         * `data.dayList[]` → HRV + resting-HR readings. Zero / absent values
         * are skipped (COROS reports 0 for a night without a reading).
         */
        fun parseDayList(json: JSONObject, sourceId: String, zone: ZoneId): List<MetricReading> {
            val dayList = json.optJSONObject("data")?.optJSONArray("dayList") ?: return emptyList()
            return (0 until dayList.length())
                .mapNotNull { dayList.optJSONObject(it) }
                .flatMap { parseDay(it, sourceId, zone) }
        }

        private fun parseDay(day: JSONObject, sourceId: String, zone: ZoneId): List<MetricReading> {
            val happenDay = day.optInt("happenDay", 0)
            val timestamp = wakeDayTimestamp(happenDay, zone) ?: return emptyList()
            val readings = mutableListOf<MetricReading>()
            val hrv = day.optDouble("avgSleepHrv", 0.0)
            if (hrv > 0) {
                readings += reading(MetricType.HEART_RATE_VARIABILITY, hrv, happenDay, timestamp, sourceId)
            }
            val rhr = day.optInt("rhr", 0)
            if (rhr > 0) {
                readings += reading(MetricType.RESTING_HEART_RATE, rhr.toDouble(), happenDay, timestamp, sourceId)
            }
            return readings
        }

        private fun reading(
            type: MetricType, value: Double, happenDay: Int, timestamp: Long, sourceId: String
        ) = MetricReading(
            id = readingId(type.key, happenDay),
            metricType = type.key,
            value = value,
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level
        )

        fun wakeDayTimestamp(happenDay: Int, zone: ZoneId): Long? {
            if (happenDay < 19700101) return null
            val date = try {
                LocalDate.parse(happenDay.toString(), DAY_FORMAT)
            } catch (_: Exception) {
                return null
            }
            return date.atTime(READING_TIME).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
