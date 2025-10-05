package com.cuibluetooth.bleeconomy.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class LoginRepository @JvmOverloads constructor(
    context: Context,
    private val verificationEndpoint: String = DEFAULT_VERIFICATION_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class CachedCredentials(
        val permit: String,
        val uuid: String,
        val expiryTimestamp: Long?,
        val lastVerifiedAt: Long?,
    )

    sealed class VerificationResult {
        data class Success(val expiryTimestamp: Long?) : VerificationResult()

        sealed class Failure : VerificationResult() {
            data class InvalidCredentials(val message: String? = null) : Failure()
            data class NetworkError(val cause: Throwable) : Failure()
            data class UnexpectedResponse(val message: String? = null) : Failure()
            data class ConfigurationError(val message: String) : Failure()
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun getCachedCredentials(): CachedCredentials? {
        val permit = prefs.getString(KEY_PERMIT, null) ?: return null
        val uuid = prefs.getString(KEY_UUID, null) ?: return null
        val expiryTimestamp = if (prefs.contains(KEY_EXPIRY)) {
            prefs.getLong(KEY_EXPIRY, NO_TIMESTAMP).takeIf { it != NO_TIMESTAMP }
        } else null
        val lastVerifiedAt = if (prefs.contains(KEY_LAST_VERIFIED)) {
            prefs.getLong(KEY_LAST_VERIFIED, NO_TIMESTAMP).takeIf { it != NO_TIMESTAMP }
        } else null
        return CachedCredentials(
            permit = permit,
            uuid = uuid,
            expiryTimestamp = expiryTimestamp,
            lastVerifiedAt = lastVerifiedAt,
        )
    }

    fun needsRevalidation(maxAge: Duration = DEFAULT_MAX_AGE): Boolean {
        val cached = getCachedCredentials() ?: return true
        val lastVerifiedAt = cached.lastVerifiedAt ?: return true
        return clock() - lastVerifiedAt > maxAge.inWholeMilliseconds
    }

    fun saveCredentials(
        permit: String,
        uuid: String,
        expiryTimestamp: Long?,
        verifiedAtMillis: Long = clock(),
    ) {
        prefs.edit().apply {
            putString(KEY_PERMIT, permit)
            putString(KEY_UUID, uuid)
            if (expiryTimestamp != null) {
                putLong(KEY_EXPIRY, expiryTimestamp)
            } else {
                remove(KEY_EXPIRY)
            }
            putLong(KEY_LAST_VERIFIED, verifiedAtMillis)
            apply()
        }
    }

    suspend fun verifyPermit(permit: String, uuid: String): VerificationResult =
        withContext(Dispatchers.IO) {
            if (verificationEndpoint.isBlank()) {
                return@withContext VerificationResult.Failure.ConfigurationError(
                    "AWS verification endpoint is not configured."
                )
            }

            val payload = buildRequestPayload(permit, uuid)
            val responseBody = try {
                executeRequest(payload)
            } catch (io: IOException) {
                return@withContext VerificationResult.Failure.NetworkError(io)
            } catch (ex: Exception) {
                return@withContext VerificationResult.Failure.NetworkError(ex)
            }

            val result = try {
                parseVerificationResponse(responseBody)
            } catch (ex: Exception) {
                return@withContext VerificationResult.Failure.UnexpectedResponse(ex.message)
            }

            when (result) {
                is ParsedVerification.Success -> {
                    saveCredentials(permit, uuid, result.expiryTimestamp)
                    VerificationResult.Success(result.expiryTimestamp)
                }

                is ParsedVerification.Invalid ->
                    VerificationResult.Failure.InvalidCredentials(result.message)

                is ParsedVerification.Unexpected ->
                    VerificationResult.Failure.UnexpectedResponse(result.message)
            }
        }

    private fun buildRequestPayload(permit: String, uuid: String): String =
        JsonObject(
            mapOf(
                "permit" to JsonPrimitive(permit),
                "uuid" to JsonPrimitive(uuid),
            )
        ).toString()

    private fun executeRequest(payload: String): String {
        val url = URL(verificationEndpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return connection.use { conn ->
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in HTTP_SUCCESS_RANGE) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw IOException("HTTP $responseCode ${conn.responseMessage}: $body")
            }

            body
        }
    }

    private fun parseVerificationResponse(raw: String): ParsedVerification {
        if (raw.isBlank()) return ParsedVerification.Unexpected("Empty verification response")

        val element = json.parseToJsonElement(raw)
        val obj = element.jsonObject

        val valid = obj["valid"]?.jsonPrimitive?.booleanOrNull
            ?: obj["isValid"]?.jsonPrimitive?.booleanOrNull
            ?: obj["success"]?.jsonPrimitive?.booleanOrNull

        val message = obj["message"]?.jsonPrimitive?.contentOrNull
            ?: obj["reason"]?.jsonPrimitive?.contentOrNull

        val expiry = obj["expiryTimestamp"]?.jsonPrimitive?.let(::parseEpochMillis)
            ?: obj["expiry"]?.jsonPrimitive?.let(::parseEpochMillis)
            ?: obj["expiresAt"]?.jsonPrimitive?.let(::parseEpochMillis)

        return when (valid) {
            true -> ParsedVerification.Success(expiry)
            false -> ParsedVerification.Invalid(message)
            null -> ParsedVerification.Unexpected("Missing validity flag in verification response")
        }
    }

    private fun parseEpochMillis(primitive: JsonPrimitive): Long? {
        primitive.longOrNull?.let { return it }
        return primitive.contentOrNull?.toLongOrNull()
    }

    private sealed class ParsedVerification {
        data class Success(val expiryTimestamp: Long?) : ParsedVerification()
        data class Invalid(val message: String?) : ParsedVerification()
        data class Unexpected(val message: String) : ParsedVerification()
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val PREFS_NAME = "login_prefs"
        private const val KEY_PERMIT = "permit"
        private const val KEY_UUID = "uuid"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_LAST_VERIFIED = "last_verified"
        private const val NO_TIMESTAMP = -1L

        private const val NETWORK_TIMEOUT_MS = 10_000
        private val HTTP_SUCCESS_RANGE = 200..299
        private val DEFAULT_MAX_AGE: Duration = 7.days

        private val DEFAULT_VERIFICATION_URL: String = BuildConfig.AWS_VERIFICATION_URL
    }
}