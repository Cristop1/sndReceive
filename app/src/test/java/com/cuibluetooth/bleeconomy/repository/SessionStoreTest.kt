package com.cuibluetooth.bleeconomy.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: TestScope
    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: SessionStore
    private var currentTime = 0L

    @Before
    fun setUp() {
        scope = TestScope(dispatcher)
        tempDir = Files.createTempDirectory("sessionStoreTest").toFile()
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "session.preferences_pb")
        }
        sessionStore = SessionStore(dataStore) { currentTime }
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun saveAndReadSession() = scope.runTest {
        val session = SessionStore.Session(
            username = "testUser",
            displayName = "Test User",
            userUuid = "user-uuid",
            assignedUuid = "assigned-uuid",
            permitCode = "VALID_PERMIT",
            expiryEpochMillis = 10_000L
        )

        sessionStore.saveSession(session)
        advanceUntilIdle()

        val stored = sessionStore.sessionFlow.firstOrNull()
        assertEquals(session, stored)
    }

    @Test
    fun clearSessionRemovesData() = scope.runTest {
        val session = SessionStore.Session(
            username = "testUser",
            displayName = "Test User",
            userUuid = "user-uuid",
            assignedUuid = "assigned-uuid",
            permitCode = "VALID_PERMIT",
            expiryEpochMillis = 10_000L
        )

        sessionStore.saveSession(session)
        advanceUntilIdle()
        sessionStore.clearSession()
        advanceUntilIdle()

        val stored = sessionStore.sessionFlow.firstOrNull()
        assertNull(stored)
    }

    @Test
    fun isSessionExpiredReflectsExpiryTimestamp() = scope.runTest {
        val activeSession = SessionStore.Session(
            username = "active",
            displayName = null,
            userUuid = "user-uuid",
            assignedUuid = "assigned-uuid",
            permitCode = "VALID_PERMIT",
            expiryEpochMillis = 5_000L
        )
        currentTime = 4_000L
        assertFalse(sessionStore.isSessionExpired(activeSession, currentTime))

        currentTime = 5_100L
        assertTrue(sessionStore.isSessionExpired(activeSession, currentTime))

        assertFalse(sessionStore.isSessionExpired(null, currentTime))
    }
}