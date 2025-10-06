package com.cuibluetooth.bleeconomy.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cuibluetooth.bleeconomy.repository.AuthRepository
import com.cuibluetooth.bleeconomy.repository.PermitRepository
import com.cuibluetooth.bleeconomy.repository.SessionStore
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: TestScope
    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        scope = TestScope(dispatcher)
        tempDir = Files.createTempDirectory("loginViewModelTest").toFile()
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "session.preferences_pb")
        }
        sessionStore = SessionStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun successfulLoginSavesSessionAndEmitsSuccess() = scope.runTest {
        val viewModel = LoginViewModel(
            authRepository = AuthRepository(),
            permitRepository = PermitRepository(),
            sessionStore = sessionStore,
            dispatcher = dispatcher
        )

        viewModel.submit("user@example.com", "password", "VALID_PERMIT")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Success)
        val saved = sessionStore.sessionFlow.firstOrNull()
        assertEquals("user@example.com", saved?.username)
        assertEquals("VALID_PERMIT", saved?.permitCode)
    }

    @Test
    fun invalidPermitEmitsError() = scope.runTest {
        val viewModel = LoginViewModel(
            authRepository = AuthRepository(),
            permitRepository = PermitRepository(),
            sessionStore = sessionStore,
            dispatcher = dispatcher
        )

        viewModel.submit("user@example.com", "password", "INVALID")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        val saved = sessionStore.sessionFlow.firstOrNull()
        assertNull(saved)
    }
}