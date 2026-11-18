package com.sandbox.patchsim.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.TargetApp
import com.sandbox.patchsim.data.db.SnapshotEntity
import com.sandbox.patchsim.data.repo.SandboxRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BackupUiState(
    val snapshots: List<SnapshotEntity> = emptyList(),
    val targets: List<TargetApp> = emptyList(),
    val message: String? = null
)

class BackupViewModel(private val repo: SandboxRepository) : ViewModel() {

    private val messages = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BackupUiState> =
        combine(repo.snapshots, repo.targets, messages) { snaps, targets, msg ->
            BackupUiState(snaps, targets, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupUiState())

    fun backup(pkg: String, label: String) {
        viewModelScope.launch {
            repo.saveSnapshot(pkg, label.ifBlank { "Backup manual" })
            messages.value = "Backup salvo para $pkg"
        }
    }

    fun restore(snapshot: SnapshotEntity) {
        viewModelScope.launch {
            repo.restoreSnapshot(snapshot)
            messages.value = "Restaurado '${snapshot.label}'"
        }
    }

    fun delete(snapshot: SnapshotEntity) {
        viewModelScope.launch { repo.deleteSnapshot(snapshot) }
    }
}
