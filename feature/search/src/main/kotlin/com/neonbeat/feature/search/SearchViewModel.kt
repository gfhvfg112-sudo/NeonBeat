package com.neonbeat.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonbeat.domain.repository.SearchRepository
import com.neonbeat.domain.repository.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Instant search across songs, albums, artists, genres, folders and playlists.
 *
 * The query flow is debounced by 120 ms and de-duplicated before it reaches the
 * FTS index. That is short enough to feel instantaneous while collapsing the
 * burst of keystrokes that would otherwise fire one query per character on a
 * 100k-row table. `flatMapLatest` cancels any in-flight query as soon as the
 * text changes, so results can never arrive out of order.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce { text -> if (text.isEmpty()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.length < MIN_QUERY_LENGTH) {
                flowOf(SearchResults())
            } else {
                searchRepository.search(text)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    val recentQueries: StateFlow<List<String>> = searchRepository.recentQueries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clear() {
        _query.value = ""
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
        const val MIN_QUERY_LENGTH = 1
    }
}
