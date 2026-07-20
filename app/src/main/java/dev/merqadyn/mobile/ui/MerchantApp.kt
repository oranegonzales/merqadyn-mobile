package dev.merqadyn.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.merqadyn.mobile.R
import dev.merqadyn.mobile.data.MerchantSnapshot
import dev.merqadyn.mobile.data.ProductDraft
import dev.merqadyn.mobile.ui.theme.Chalk
import dev.merqadyn.mobile.ui.theme.Ink
import dev.merqadyn.mobile.ui.theme.Paper
import dev.merqadyn.mobile.ui.theme.Rust

private enum class AppSection(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Outlined.GridView),
    Inventory("Inventory", Icons.Outlined.Inventory2),
    Catalog("Catalog", Icons.Outlined.Category),
    Queue("Queue", Icons.Outlined.Sync),
}

@Composable
fun MerchantApp(
    snapshot: MerchantSnapshot,
    busy: Boolean,
    notice: String?,
    onClearNotice: () -> Unit,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onAdjustStock: (String, String, Double, String) -> Unit,
    onCreateProduct: (ProductDraft) -> Unit,
    onUpdateProduct: (String, ProductDraft) -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onRemoveEnrollment: () -> Unit,
) {
    var section by remember { mutableStateOf(AppSection.Overview) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onClearNotice()
        }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_merqadyn_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MERQADYN",
                            color = Chalk,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = snapshot.syncState.merchantName ?: "Merchant operations",
                            color = Chalk.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Rust,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onSync) {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync records", tint = Chalk)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Ink,
                tonalElevation = 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                AppSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Chalk,
                            selectedTextColor = Chalk,
                            indicatorColor = Rust,
                            unselectedIconColor = Chalk.copy(alpha = 0.52f),
                            unselectedTextColor = Chalk.copy(alpha = 0.52f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (section) {
                AppSection.Overview -> OverviewScreen(snapshot, onRefresh, onSync)
                AppSection.Inventory -> InventoryScreen(snapshot.inventory, onAdjustStock)
                AppSection.Catalog -> CatalogScreen(snapshot.products, snapshot.syncState.currency, onCreateProduct, onUpdateProduct)
                AppSection.Queue -> QueueScreen(snapshot.mutations, snapshot.syncState, onSync, onRetry, onDismiss, onRemoveEnrollment)
            }
        }
    }
}
