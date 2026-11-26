package com.chinoisfacile.mandarin.data.local

import android.content.Context
import com.chinoisfacile.mandarin.data.model.GrammarNote
import com.chinoisfacile.mandarin.data.model.GrammarNotesCatalogDto
import com.chinoisfacile.mandarin.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads curated grammar/sentence-pattern notes from the offline asset catalog
 * and caches them in memory indexed by (hskLevel, triggerWord).
 */
class GrammarNotesLoader(
    private val context: Context,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedNotesByLevel: Map<Int, Map<String, GrammarNote>>? = null

    suspend fun getNotesByTrigger(hskLevel: Int): Map<String, GrammarNote> {
        cachedNotesByLevel?.let { return it[hskLevel].orEmpty() }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val existing = cachedNotesByLevel
                if (existing != null) {
                    return@withLock existing[hskLevel].orEmpty()
                }
                val loaded = loadFromAssets()
                cachedNotesByLevel = loaded
                loaded[hskLevel].orEmpty()
            }
        }
    }

    private fun loadFromAssets(): Map<Int, Map<String, GrammarNote>> = runCatching {
        val rawJson = context.assets.open(GRAMMAR_ASSET_PATH)
            .bufferedReader()
            .use { it.readText() }
        val dto = json.decodeFromString<GrammarNotesCatalogDto>(rawJson)
        dto.notes
            .map { it.toDomain() }
            .groupBy { it.hskLevel }
            .mapValues { (_, notes) -> notes.associateBy { it.triggerWord } }
    }.getOrElse { emptyMap() }

    companion object {
        const val GRAMMAR_ASSET_PATH = "catalog/grammar_notes.json"
    }
}
