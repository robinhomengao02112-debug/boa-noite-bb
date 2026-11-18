package com.sandbox.patchsim.feature.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.LicenseVerdict
import com.sandbox.patchsim.data.db.LicenseConfigEntity
import com.sandbox.patchsim.data.repo.SandboxRepository
import com.sandbox.patchsim.data.server.MockLicenseServer
import com.sandbox.patchsim.feature.demo.DemoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class LicenseUiState(
    val configs: List<LicenseConfigEntity> = emptyList(),
    val serverRunning: Boolean = false,
    val serverUrl: String = "parado",
    val lastResponse: String? = null
)

class LicenseViewModel(
    private val repo: SandboxRepository,
    private val server: MockLicenseServer
) : ViewModel() {

    private val serverFlow = MutableStateFlow(snapshot())
    private val response = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LicenseUiState> =
        combine(repo.licenseConfigs, serverFlow, response) { configs, s, resp ->
            LicenseUiState(configs, s.first, s.second, resp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LicenseUiState())

    fun setVerdict(pkg: String, verdict: LicenseVerdict) {
        viewModelScope.launch { repo.setLicenseVerdict(pkg, verdict) }
    }

    fun toggleServer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (server.isRunning) server.stop() else server.start()
            serverFlow.value = snapshot()
        }
    }

    /** Perform a real loopback request to validate the running mock server. */
    fun testCheck(pkg: String = DemoApp.PACKAGE) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!server.isRunning) { server.start(); serverFlow.value = snapshot() }
            val nonce = System.nanoTime().toString()
            val body = withContext(Dispatchers.IO) {
                runCatching {
                    URL("${server.baseUrl}/license?package=$pkg&nonce=$nonce").readText()
                }.getOrElse { "erro: ${it.message}" }
            }
            response.value = body
        }
    }

    private fun snapshot(): Pair<Boolean, String> =
        server.isRunning to if (server.isRunning) server.baseUrl else "parado"
}
