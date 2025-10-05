package com.cuibluetooth.bleeconomy.repository
import kotlinx.coroutines.delay

/**
 * Repository responsible for validating permit codes with the backend.
 *
 * The current implementation simulates a remote validation while providing
 * a single source of truth for the view models to depend on. Replace the
 * implementation with real networking logic when integrating the backend.
 */

class PermitRepository {
    data class PermitVerificationResult(
        val permitCode: String,
        val assignedUuid: String,
        val expiryEpochMillis: Long?
    )

    /**
     * Simulate a remote permit validation.
     */
    suspend fun verify(permitCode: String, assignedUuid: String): PermitVerificationResult {
        // Simulate network latency so the UI can react to loading state.
        delay(300)

        if (permitCode.equals("VALID_PERMIT", ignoreCase = true) && assignedUuid.isNotEmpty()) {
            return PermitVerificationResult(
                permitCode = permitCode,
                assignedUuid = assignedUuid,
                expiryEpochMillis = System.currentTimeMillis() + 86_400_000 // +24h
            )
        }

        throw IllegalArgumentException("Invalid permit code or device identifier")
    }
}