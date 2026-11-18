package com.sandbox.patchsim.feature.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun PurchaseScreen() {
    val vm: PurchaseViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        state.lastMessage?.let {
            AssistChip(onClick = {}, label = { Text(it) }, modifier = Modifier.padding(16.dp))
        }
        SectionCard("Catálogo de teste", "Preços fictícios. Nenhum pagamento real é feito.") {
            state.products.forEach { product ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(product.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${product.sku} — ${vm.priceLabel(product.priceCents, product.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    TextButton(onClick = { vm.buy(product, asFree = false) }) { Text("Comprar") }
                    TextButton(onClick = { vm.buy(product, asFree = true) }) { Text("R$ 0,00") }
                }
                HorizontalDivider()
            }
        }
        SectionCard("Compras simuladas (${state.purchases.size})") {
            if (state.purchases.isEmpty()) {
                Text("Nenhuma compra ainda.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.purchases.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.sku} · ${p.state}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${p.orderId} · ${vm.priceLabel(p.priceCents, p.currency)}",
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                            )
                        }
                        if (p.state.name == "PURCHASED") {
                            TextButton(onClick = { vm.refund(p.orderId) }) { Text("Reembolsar") }
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::clearAll) { Text("Limpar histórico") }
            }
        }
    }
}
