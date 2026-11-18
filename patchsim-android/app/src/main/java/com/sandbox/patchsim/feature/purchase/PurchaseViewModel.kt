package com.sandbox.patchsim.feature.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.SimProduct
import com.sandbox.patchsim.core.model.SimPurchase
import com.sandbox.patchsim.data.repo.SandboxRepository
import com.sandbox.patchsim.feature.demo.DemoApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PurchaseUiState(
    val products: List<SimProduct> = emptyList(),
    val purchases: List<SimPurchase> = emptyList(),
    val lastMessage: String? = null
)

class PurchaseViewModel(private val repo: SandboxRepository) : ViewModel() {

    private val messages = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PurchaseUiState> =
        combine(repo.products, repo.purchases, messages) { products, purchases, msg ->
            PurchaseUiState(products, purchases, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurchaseUiState())

    fun buy(product: SimProduct, asFree: Boolean) {
        viewModelScope.launch {
            val override = if (asFree) 0L else null
            val p = repo.simulatePurchase(product.sku, DemoApp.PACKAGE, override)
            messages.value = "Compra simulada: ${product.title} -> ${SandboxRepository.formatPrice(p.priceCents, p.currency)}"
        }
    }

    fun refund(orderId: String) {
        viewModelScope.launch {
            repo.refund(orderId)
            messages.value = "Pedido $orderId reembolsado (simulado)"
        }
    }

    fun clearAll() {
        viewModelScope.launch { repo.clearPurchases(); messages.value = "Histórico de compras limpo" }
    }

    fun priceLabel(cents: Long, currency: String) = SandboxRepository.formatPrice(cents, currency)
}
