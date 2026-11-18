package com.sandbox.patchsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sandbox.patchsim.feature.analyzer.AnalyzerScreen
import com.sandbox.patchsim.feature.backup.BackupScreen
import com.sandbox.patchsim.feature.dashboard.DashboardScreen
import com.sandbox.patchsim.feature.demo.DemoScreen
import com.sandbox.patchsim.feature.license.LicenseScreen
import com.sandbox.patchsim.feature.logs.LogsScreen
import com.sandbox.patchsim.feature.patch.PatchScreen
import com.sandbox.patchsim.feature.purchase.PurchaseScreen
import com.sandbox.patchsim.feature.subscription.SubscriptionScreen
import com.sandbox.patchsim.ui.theme.PatchSimTheme
import kotlinx.coroutines.launch

private data class Dest(val route: String, val title: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("dashboard", "Painel", Icons.Filled.Menu),
    Dest("analyzer", "Analisador de APKs", Icons.AutoMirrored.Filled.List),
    Dest("subscription", "Assinaturas", Icons.Filled.Menu),
    Dest("purchase", "Compras", Icons.Filled.Menu),
    Dest("patch", "Patches", Icons.Filled.Menu),
    Dest("license", "Licenças", Icons.Filled.Menu),
    Dest("backup", "Backup", Icons.Filled.Menu),
    Dest("demo", "Demo Premium", Icons.Filled.Menu),
    Dest("logs", "Logs", Icons.Filled.Menu)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PatchSimTheme { AppScaffold() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"
    val currentTitle = DESTS.firstOrNull { it.route == currentRoute }?.title ?: "PatchSim Lab"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("PatchSim Lab", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                DESTS.forEach { dest ->
                    NavigationDrawerItem(
                        label = { Text(dest.title) },
                        selected = dest.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(dest.route) {
                                popUpTo("dashboard"); launchSingleTop = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentTitle) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(padding)
            ) {
                composable("dashboard") { DashboardScreen() }
                composable("analyzer") { AnalyzerScreen() }
                composable("subscription") { SubscriptionScreen() }
                composable("purchase") { PurchaseScreen() }
                composable("patch") { PatchScreen() }
                composable("license") { LicenseScreen() }
                composable("backup") { BackupScreen() }
                composable("demo") { DemoScreen() }
                composable("logs") { LogsScreen() }
            }
        }
    }
}
