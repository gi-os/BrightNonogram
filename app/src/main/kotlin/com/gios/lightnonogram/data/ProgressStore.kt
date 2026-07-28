package com.gios.lightnonogram.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gios.lightnonogram.game.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which puzzles the player has finished.
 *
 * Backed by the `DataStore<Preferences>` the SDK hands every screen. Progress is
 * the only thing here written often, and it's a short list of ids — a database
 * would be ceremony for no benefit.
 */
class ProgressStore(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("completed_puzzle_ids")

    val progress: Flow<Progress> = dataStore.data.map { Progress.decode(it[key]) }

    suspend fun markSolved(puzzleId: String) {
        // Read-modify-write inside edit{} so two quick wins can't clobber each other.
        dataStore.edit { prefs ->
            prefs[key] = Progress.decode(prefs[key]).with(puzzleId).encode()
        }
    }

    suspend fun resetAll() {
        dataStore.edit { it.remove(key) }
    }
}
