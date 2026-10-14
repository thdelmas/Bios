package com.bios.app.ingest

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * The COROS *mobile* API, the host the COROS phone app talks to. The
 * Training Hub web API ([CorosApiAdapter]) carries HRV and resting HR but
 * no steps; daily statistics (steps among them) live here
 * (docs/specs/source-preference.md, criterion 2b).
 *
 * Sign-in imitates the app: e-mail and MD5(password) are XORed with a
 * random 16-digit app key, PKCS7-padded, AES-128-CBC encrypted with that
 * key and a fixed IV, base64'd. Scheme and device fingerprint come from
 * the community client (cygnusb/coros-mcp, read 2026-10-14); the vendor
 * may change either at any time, which the spec prices as a limit row.
 */
internal class CorosMobileApi(private val client: OkHttpClient) {

    data class Login(val token: String)

    /** Signs in; null when the vendor refuses or the transport fails. */
    fun login(email: String, password: String, region: String): Result<Login> = runCatching {
        val base = MOBILE_BASE_URLS[region] ?: error("Unknown COROS region '$region'")
        val appKey = randomAppKey()
        val payload = JSONObject()
            .put("account", encrypt(email, appKey) + "\n")
            .put("accountType", 2)
            .put("appKey", appKey)
            .put("clientType", 1)
            .put("hasHrCalibrated", 0)
            .put("kbValidity", 0)
            .put("pwd", encrypt(CorosApiAdapter.md5Hex(password), appKey) + "\n")
            .put("region", DEVICE_REGION)
            .put("skipValidation", false)
        val request = Request.Builder()
            .url(base + LOGIN_PATH)
            .header("user-agent", USER_AGENT)
            .header("request-time", System.currentTimeMillis().toString())
            .header("yfheader", YF_HEADER)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val json = client.getJson(request) ?: error("COROS mobile login: empty or non-2xx response")
        val result = json.optString("result")
        if (result != CorosApiAdapter.RESULT_OK) error("COROS mobile login refused ($result): ${json.optString("message")}")
        val token = json.optJSONObject("data")?.optString("accessToken").orEmpty()
        if (token.isBlank()) error("COROS mobile login: no accessToken in response")
        Login(token)
    }

    /**
     * Daily statistics for [startDay]..[endDay] (yyyyMMdd, inclusive). The
     * raw body is returned so the caller can read the result code (1019 =
     * token expired) and parse what it needs.
     */
    fun statisticDaily(token: String, region: String, startDay: Int, endDay: Int, dataTypes: List<Int>): JSONObject? {
        val base = MOBILE_BASE_URLS[region] ?: return null
        val payload = JSONObject()
            .put("allDeviceSleep", 1)
            .put("dataType", JSONArray(dataTypes))
            .put("dataVersion", 0)
            .put("startTime", startDay)
            .put("endTime", endDay)
            .put("statisticType", 1)
        val request = Request.Builder()
            .url("$base$DAILY_PATH?accessToken=$token")
            .header("accesstoken", token)
            .header("user-agent", USER_AGENT)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return client.getJson(request)
    }

    companion object {
        val MOBILE_BASE_URLS: Map<String, String> = mapOf(
            "eu" to "https://apieu.coros.com",
            "us" to "https://api.coros.com",
            "cn" to "https://apicn.coros.com",
        )
        const val LOGIN_PATH = "/coros/user/login"
        const val DAILY_PATH = "/coros/data/statistic/daily"
        /** Token expired or invalid; the caller signs in again once. */
        const val RESULT_TOKEN_INVALID = "1019"

        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val USER_AGENT = "okhttp/4.12.0"
        private val IV = "weloop3_2015_03#".toByteArray(Charsets.US_ASCII)
        /** Device telemetry the app sends; routing is by host, not by this field. */
        private const val DEVICE_REGION = "310|Europe/Berlin|US"
        private const val YF_HEADER =
            """{"appVersion":1125917087236096,"clientType":1,"language":"en-US",""" +
                """"mobileName":"sdk_gphone64_arm64,google,Google","releaseType":1,""" +
                """"systemVersion":"13","timezone":4,"versionCode":"404080400"}"""
        private const val APP_KEY_DIGITS = 16

        fun randomAppKey(): String = buildString {
            append(Random.nextInt(1, 10))
            repeat(APP_KEY_DIGITS - 1) { append(Random.nextInt(0, 10)) }
        }

        /** XOR with the key, PKCS7 pad, AES-128-CBC with the fixed IV, base64. Pure, for tests. */
        fun encrypt(plaintext: String, appKey: String): String {
            val key = appKey.toByteArray(Charsets.US_ASCII)
            val data = plaintext.toByteArray(Charsets.UTF_8)
            val xored = ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV))
            return Base64.getEncoder().encodeToString(cipher.doFinal(xored))
        }
    }
}
