package com.chinoisfacile.mandarin.ui.lexicon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.lexicon.LexiconFilter
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusCounts
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusFilter
import com.chinoisfacile.mandarin.domain.search.IndexedVocabWord
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LexiconViewModel(
    private val repository: MandarinRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val statusFilter = MutableStateFlow(LexiconStatusFilter.ALL)
    private val _searchQuery = MutableStateFlow("")

    /**
     * Synchronous flow dedicated to text-field input so rapid typing never glitches cursor position.
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val selectedWordId = MutableStateFlow<String?>(null)
    private val selectedWordTokenLookup = MutableStateFlow<Map<String, VocabWord>>(emptyMap())
    private val latestWordsById = MutableStateFlow<Map<String, WordWithProgress>>(emptyMap())
    private var wordLookupJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    private val levelIndexFlow = repository.observeSelectedHskLevel()
        .flatMapLatest { level ->
            repository.observeLexiconWords(level).map { words ->
                val today = LocalDate.now().toEpochDay()
                IndexedLevelWords(
                    hskLevel = level,
                    todayEpochDay = today,
                    counts = LexiconFilter.computeCounts(words, today),
                    indexed = LexiconFilter.buildSortedIndex(words),
                    wordsById = words.associateBy { it.word.id },
                )
            }
        }
        .onEach { latestWordsById.value = it.wordsById }
        .flowOn(Dispatchers.Default)

    private val debouncedQueryFlow = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()

    private val filteredWordsFlow = combine(
        levelIndexFlow,
        statusFilter,
        debouncedQueryFlow,
    ) { levelData, filter, query ->
        FilteredLexiconSnapshot(
            hskLevel = levelData.hskLevel,
            statusFilter = filter,
            counts = levelData.counts,
            filteredWords = LexiconFilter.filterIndexed(
                indexed = levelData.indexed,
                statusFilter = filter,
                searchQuery = query,
                todayEpochDay = levelData.todayEpochDay,
            ),
            wordsById = levelData.wordsById,
        )
    }
        .flowOn(Dispatchers.Default)

    private val selectionStateFlow = combine(
        selectedWordId,
        selectedWordTokenLookup,
    ) { wordId, tokenLookup ->
        wordId to tokenLookup
    }

    val uiState: StateFlow<LexiconUiState> = combine(
        filteredWordsFlow,
        repository.observeLexiconViewMode(),
        repository.observeToneColorsEnabled(),
        selectionStateFlow,
        audioPlayer.status,
    ) { snapshot, viewMode, toneColorsEnabled, selection, ttsStatus ->
        val (wordId, tokenLookup) = selection
        LexiconUiState(
            isLoading = false,
            selectedHskLevel = snapshot.hskLevel,
            viewMode = viewMode,
            statusFilter = snapshot.statusFilter,
            counts = snapshot.counts,
            filteredWords = snapshot.filteredWords,
            selectedWord = wordId?.let { snapshot.wordsById[it] },
            selectedWordTokenLookup = if (wordId != null) tokenLookup else emptyMap(),
            toneColorsEnabled = toneColorsEnabled,
            ttsStatus = ttsStatus,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LexiconUiState(),
        )

    fun onSelectHskLevel(level: Int) {
        wordLookupJob?.cancel()
        selectedWordId.value = null
        selectedWordTokenLookup.value = emptyMap()
        viewModelScope.launch {
            repository.setSelectedHskLevel(level)
        }
    }

    fun onToggleViewMode() {
        val next = when (uiState.value.viewMode) {
            LexiconViewMode.GRID -> LexiconViewMode.COMPACT_LIST
            LexiconViewMode.COMPACT_LIST -> LexiconViewMode.GRID
        }
        viewModelScope.launch {
            repository.setLexiconViewMode(next)
        }
    }

    fun onSelectStatusFilter(filter: LexiconStatusFilter) {
        statusFilter.value = filter
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onWordClick(wordId: String) {
        wordLookupJob?.cancel()
        val selected = latestWordsById.value[wordId] ?: return
        selectedWordId.value = wordId
        selectedWordTokenLookup.value = emptyMap()

        val tokens = selected.word.exampleTokens
            .asSequence()
            .filter { it.isNotBlank() && it != selected.word.hanzi }
            .distinct()
            .toList()
        if (tokens.isEmpty()) return

        wordLookupJob = viewModelScope.launch {
            val lookup = repository.getWordsByHanzi(tokens)
            if (selectedWordId.value == wordId) {
                selectedWordTokenLookup.value = lookup
            }
        }
    }

    fun onDismissWordDetail() {
        wordLookupJob?.cancel()
        selectedWordId.value = null
        selectedWordTokenLookup.value = emptyMap()
    }

    fun onPlayNormal(text: String) {
        audioPlayer.speakNormal(text)
    }

    fun onPlaySlow(text: String) {
        audioPlayer.speakSlow(text)
    }

    private data class IndexedLevelWords(
        val hskLevel: Int,
        val todayEpochDay: Long,
        val counts: LexiconStatusCounts,
        val indexed: List<IndexedVocabWord>,
        val wordsById: Map<String, WordWithProgress>,
    )

    private data class FilteredLexiconSnapshot(
        val hskLevel: Int,
        val statusFilter: LexiconStatusFilter,
        val counts: LexiconStatusCounts,
        val filteredWords: List<WordWithProgress>,
        val wordsById: Map<String, WordWithProgress>,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
