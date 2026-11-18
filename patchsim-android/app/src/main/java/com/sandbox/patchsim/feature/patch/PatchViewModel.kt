package com.sandbox.patchsim.feature.patch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.PatchDefinition
import com.sandbox.patchsim.core.model.PatchType
import com.sandbox.patchsim.core.model.TargetApp
import com.sandbox.patchsim.data.repo.SandboxRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PatchUiState(
    val patches: List<PatchDefinition> = emptyList(),
    val targets: List<TargetApp> = emptyList(),
    val message: String? = null
)

class PatchViewModel(private val repo: SandboxRepository) : ViewModel() {

    private val messages = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PatchUiState> =
        combine(repo.patches, repo.targets, messages) { patches, targets, msg ->
            PatchUiState(patches, targets, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatchUiState())

    fun apply(patch: PatchDefinition) {
        viewModelScope.launch {
            val result = repo.applyPatch(patch)
            messages.value = "Aplicado '${patch.name}': premium ${result.before.premiumUnlocked} → ${result.after.premiumUnlocked}"
        }
    }

    fun revert(patch: PatchDefinition) {
        viewModelScope.launch {
            repo.revertPatch(patch)
            messages.value = "Revertido '${patch.name}' a partir do backup"
        }
    }

    fun create(name: String, type: PatchType, targetPackage: String, tier: String?) {
        viewModelScope.launch {
            val payload = buildMap {
                if (type == PatchType.FORCE_SUBSCRIPTION && tier != null) {
                    put("tier", tier); put("status", "ACTIVE")
                }
            }
            repo.savePatch(PatchDefinition(name = name, type = type, targetPackage = targetPackage, payload = payload))
            messages.value = "Patch '$name' criado"
        }
    }

    fun delete(patch: PatchDefinition) {
        viewModelScope.launch { repo.deletePatch(patch) }
    }
}
