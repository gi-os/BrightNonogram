package com.gios.lightnonogram.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gios.lightnonogram.game.Made
import com.gios.lightnonogram.game.MadeCollection
import com.gios.lightnonogram.game.Progress
import com.gios.lightnonogram.game.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the tool remembers between launches. */
data class ToolState(
    val progress: Progress = Progress(),
    val made: MadeCollection = MadeCollection(),
    val autoCross: Boolean = true,
    val size: Int = 10,
    /** The board the player last touched, if they left it unfinished. */
    val session: Session? = null,
)

/**
 * The tool's persisted state, on the `DataStore<Preferences>` the SDK hands every
 * screen.
 *
 * All of it is small: solved puzzle ids, a list of `size:seed` pairs for
 * generated ones, and two settings. Preferences suit that shape — a database
 * would be ceremony — and it comes back as one flow the UI collects, so a single
 * read keeps every view consistent.
 */
class ProgressStore(private val dataStore: DataStore<Preferences>) {

    private val completedKey = stringPreferencesKey("completed_puzzle_ids")
    private val madeKey = stringPreferencesKey("made_puzzles")
    private val autoCrossKey = booleanPreferencesKey("auto_cross")
    private val sizeKey = intPreferencesKey("puzzle_size")
    private val sessionKey = stringPreferencesKey("session")

    val state: Flow<ToolState> = dataStore.data.map { p ->
        ToolState(
            progress = Progress.decode(p[completedKey]),
            made = MadeCollection.decode(p[madeKey]),
            autoCross = p[autoCrossKey] ?: true,
            // Ignore anything other than the two sizes that ship, so a stale or
            // hand-edited preference can't select a pack that doesn't exist.
            size = p[sizeKey]?.takeIf { it == 10 || it == 15 } ?: 10,
            session = Session.decode(p[sessionKey]),
        )
    }

    suspend fun markSolved(puzzleId: String) {
        // Read-modify-write inside edit{} so two quick wins can't clobber each other.
        dataStore.edit { p ->
            p[completedKey] = Progress.decode(p[completedKey]).with(puzzleId).encode()
        }
    }

    suspend fun addMade(made: Made) {
        dataStore.edit { p ->
            p[madeKey] = MadeCollection.decode(p[madeKey]).with(made).encode()
        }
    }

    /**
     * Remember where the player is. Written after every stroke, which is what
     * LightSolitaire does with its deal — preferences coalesce writes and the
     * payload is under a hundred characters.
     */
    suspend fun saveSession(session: Session) {
        dataStore.edit { it[sessionKey] = session.encode() }
    }

    suspend fun clearSession() {
        dataStore.edit { it.remove(sessionKey) }
    }

    suspend fun setAutoCross(enabled: Boolean) {
        dataStore.edit { it[autoCrossKey] = enabled }
    }

    suspend fun setSize(size: Int) {
        require(size == 10 || size == 15) { "unsupported size $size" }
        dataStore.edit { it[sizeKey] = size }
    }

    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }
}
