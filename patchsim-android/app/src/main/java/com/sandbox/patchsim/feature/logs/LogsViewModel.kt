package com.sandbox.patchsim.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.data.db.LogEntity
import com.sandbox.patchsim.data.repo.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(private val repo: LogRepository) : ViewModel() {
    val logs: StateFlow<List<LogEntity>> =
        repo.observeRecent(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() { viewModelScope.launch { repo.clear() } }
}
