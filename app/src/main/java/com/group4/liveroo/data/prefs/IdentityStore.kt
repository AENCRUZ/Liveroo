package com.group4.liveroo.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "identity")

/**
 * Local identity (C16): a display name plus an ID generated once on this phone (T3).
 * An interface so the repository and sync code can be tested without Android (see DataStoreIdentityStore).
 */
interface IdentityStore {
    val userId: Flow<String>
    val displayName: Flow<String>
    val onboarded: Flow<Boolean>

    suspend fun ensureId(): String
    suspend fun setName(name: String)

    /** This phone's (userId, displayName). */
    suspend fun me(): Pair<String, String>
}

/** The real identity store, backed by Preferences DataStore. */
class DataStoreIdentityStore(private val context: Context) : IdentityStore {
    private val keyId = stringPreferencesKey("user_id")
    private val keyName = stringPreferencesKey("display_name")
    private val keyOnboarded = booleanPreferencesKey("onboarded")

    private val prefs: Flow<Preferences> = context.dataStore.data

    override val userId: Flow<String> = prefs.map { it[keyId].orEmpty() }
    override val displayName: Flow<String> = prefs.map { it[keyName].orEmpty() }
    override val onboarded: Flow<Boolean> = prefs.map { it[keyOnboarded] == true }

    override suspend fun ensureId(): String {
        val existing = userId.first()
        if (existing.isNotEmpty()) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[keyId] = id }
        return id
    }

    override suspend fun setName(name: String) {
        context.dataStore.edit {
            it[keyName] = name.trim()
            it[keyOnboarded] = true
        }
    }

    override suspend fun me(): Pair<String, String> = ensureId() to displayName.first()
}
