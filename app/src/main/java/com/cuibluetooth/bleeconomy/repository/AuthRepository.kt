package com.cuibluetooth.bleeconomy.repository

import java.util.UUID
import kotlinx.coroutines.delay

/**
 * Simple authentication repository that simulates a remote check for username/password
 * credentials. Replace the implementation with a real network call when wiring the
 * production backend.
 */
class AuthRepository {
    data class AuthResult(
        val username: String,
        val displayName: String?,
        val userUuid: String
    )

    /**
     * Pretend to authenticate against a backend. In this sample implementation we accept any
     * non-blank credentials where the password has at least four characters.
     */
    suspend fun authenticate(username: String, password: String): AuthResult {
        delay(300)

        if (username.isBlank() || password.isBlank() || password.length < 4) {
            throw IllegalArgumentException("Invalid username or password")
        }

        val normalized = username.trim()
        val display = normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val uuid = UUID.nameUUIDFromBytes(normalized.lowercase().toByteArray()).toString()
        return AuthResult(
            username = normalized,
            displayName = display,
            userUuid = uuid
        )
    }
}