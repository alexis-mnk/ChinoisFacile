package com.chinoisfacile.mandarin.ui.lexicon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.lexicon.LexiconFilter
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusCounts
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusFilter
import com.chinoisfacile.mandarin.domain.search.IndexedVocabWord
import com.chinoisfacile.mandarin.domain.search.VocabularySearchFilter
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LexiconViewModel(
    private val repository: MandarinRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val statusFilter = MutableStateFlow(LexiconStatusFilter.ALL)
    private val _searchQuery = MutableStateFlow("")

    /**
     * Flux synchrone dédié uniquement à la saisie dans la barre de recherche :
     * évite tout saut de curseur lors du filtrage de gros catalogues.
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val selectedWordId = MutableStateFlow<String?>(null)

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
                    indexed = VocabularySearchFilter.buildIndex(words),
                )
            }
        }
        .flowOn(Dispatchers.Default)

    private val filterControlsFlow = combine(
        repository.observeLexiconViewMode(),
        statusFilter,
        _searchQuery,
        selectedWordId,
    ) { viewMode, filter, query, wordId ->
        FilterControls(viewMode, filter, query, wordId)
    }

    val uiState: StateFlow<LexiconUiState> = combine(
        levelIndexFlow,
        filterControlsFlow,
        audioPlayer.status,
    ) { levelData, controls, ttsStatus ->
        val filtered = LexiconFilter.filterIndexed(
            indexed = levelData.indexed,
            statusFilter = controls.statusFilter,
            searchQuery = controls.searchQuery,
            todayEpochDay = levelData.todayEpochDay,
        )
        val selectedWord = controls.selectedWordId?.let { id ->
            levelData.indexed.firstOrNull { it.item.word.id == id }?.item
        }

        LexiconUiState(
            isLoading = false,
            selectedHskLevel = levelData.hskLevel,
            viewMode = controls.viewMode,
            statusFilter = controls.statusFilter,
            counts = levelData.counts,
            filteredWords = filtered,
            selectedWord = selectedWord,
            ttsStatus = ttsStatus,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LexiconUiState(),
        )

    fun onSelectHskLevel(level: Int) {
        selectedWordId.value = null
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
        selectedWordId.value = wordId
    }

    fun onDismissWordDetail() {
        selectedWordId.value = null
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
    )

    private data class FilterControls(
        val viewMode: LexiconViewMode,
        val statusFilter: LexiconStatusFilter,
        val searchQuery: String,
        val selectedWordId: String?,
    )
}
