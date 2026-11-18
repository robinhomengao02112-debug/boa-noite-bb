package com.sandbox.patchsim.feature.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.TargetApp
import com.sandbox.patchsim.data.repo.ApkAnalyzer
import com.sandbox.patchsim.data.repo.ApkReport
import com.sandbox.patchsim.data.repo.LogRepository
import com.sandbox.patchsim.data.repo.SandboxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalyzerUiState(
    val loading: Boolean = false,
    val includeSystem: Boolean = false,
    val apps: List<ApkReport> = emptyList(),
    val selected: ApkReport? = null,
    val query: String = ""
) {
    val filtered: List<ApkReport>
        get() = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
}

class AnalyzerViewModel(
    private val analyzer: ApkAnalyzer,
    private val repo: SandboxRepository,
    private val log: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyzerUiState())
    val state: StateFlow<AnalyzerUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { analyzer.listInstalled(_state.value.includeSystem) }
            _state.value = _state.value.copy(loading = false, apps = apps)
            log.i("Analyzer", "Scanned ${apps.size} installed packages")
        }
    }

    fun setIncludeSystem(value: Boolean) {
        _state.value = _state.value.copy(includeSystem = value)
        refresh()
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun select(report: ApkReport?) { _state.value = _state.value.copy(selected = report) }

    /** Register an analyzed app as an authorized test target. */
    fun registerAsTarget(report: ApkReport) {
        viewModelScope.launch {
            repo.registerTarget(
                TargetApp(
                    packageName = report.packageName,
                    label = report.label,
                    versionName = report.versionName,
                    versionCode = report.versionCode,
                    authorizedByUser = true,
                    isDemo = false,
                    notes = "Registered from APK Analyzer"
                )
            )
        }
    }
}
