package com.cuibluetooth.bleeconomy.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
class SessionStore @JvmOverloads constructor(
    private val dataStore: DataStore<Preferences>,
    private var clock : () -> Long = { System.currentTimeMillis() }
){
    data class Session(
        val username: String,
        val displayName: String?,
        val userUuid: String,
        val assignedUuid: String,
        val permitCode: String,
        val expiryEpochMillis: Long?
    )

    val sessionFlow: Flow<Session?> = dataStore.data.map { preferences ->
        val username = preferences[KEY_USERNAME]
        val userUuid = preferences[KEY_USER_UUID]
        val assignedUuid = preferences[KEY_ASSIGNED_UUID]
        val permit = preferences[KEY_PERMIT]
        if (username == null || userUuid == null || assignedUuid == null || permit == null) {
            return@map null
        }
        Session(
            username = username.toString(),
            displayName = preferences[KEY_DISPLAY_NAME],
            userUuid = userUuid.toString(),
            assignedUuid = assignedUuid.toString(),
            permitCode = permit.toString(),
            expiryEpochMillis = preferences[KEY_EXPIRY]
        )
    }

    suspend fun saveSession(session: Session) {
        dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = session.username
            session.displayName?.let { preferences[KEY_DISPLAY_NAME] = it } ?: preferences.remove(KEY_DISPLAY_NAME)
            preferences[KEY_USER_UUID] = session.userUuid
            preferences[KEY_ASSIGNED_UUID] = session.assignedUuid
            preferences[KEY_PERMIT] = session.permitCode
            session.expiryEpochMillis?.let { preferences[KEY_EXPIRY] = it } ?: preferences.remove(KEY_EXPIRY)
        }
    }

    suspend fun getSession() : Session? =sessionFlow.first()

    suspend fun clearSession() { dataStore.edit { it.clear() }}

    fun isSessionExpired(session : Session?, currentTimeMillis : Long = clock()) : Boolean{
        val expiry = session?. expiryEpochMillis ?: return false
        return expiry <= currentTimeMillis
    }

    companion object{
        private const val DATASTORE_NAME = "session_store"

        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_USER_UUID = stringPreferencesKey("user_uuid")
        private val KEY_ASSIGNED_UUID = stringPreferencesKey("assigned_uuid")
        private val KEY_PERMIT = stringPreferencesKey("permit")
        private val KEY_EXPIRY = longPreferencesKey("expiry")

        private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

        fun getInstance(context: Context): SessionStore = SessionStore(context.sessionDataStore)
    }
}