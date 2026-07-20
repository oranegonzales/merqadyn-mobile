package dev.merqadyn.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.merqadyn.mobile.data.MerchantSnapshot
import dev.merqadyn.mobile.data.ProductDraft
import dev.merqadyn.mobile.data.local.InventoryWithPending
import dev.merqadyn.mobile.data.local.PendingMutationEntity
import dev.merqadyn.mobile.data.local.ProductEntity
import dev.merqadyn.mobile.data.local.SyncStateEntity
import dev.merqadyn.mobile.ui.theme.Chalk
import dev.merqadyn.mobile.ui.theme.Ink
import dev.merqadyn.mobile.ui.theme.Rule
import dev.merqadyn.mobile.ui.theme.Rust
import dev.merqadyn.mobile.ui.theme.Stone
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val cardShape = RoundedCornerShape(3.dp)

@Composable
fun OverviewScreen(snapshot: MerchantSnapshot, onRefresh: () -> Unit, onSync: () -> Unit) {
    val activeProducts = snapshot.products.count { it.active }
    val units = snapshot.inventory.sumOf { it.onHand }
    val value = snapshot.inventory.sumOf { row ->
        val price = snapshot.products.firstOrNull { it.id == row.productId }?.price ?: 0.0
        row.onHand * price
    }
    val lowStock = snapshot.inventory.count { it.available <= 10 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "TODAY'S LEDGER",
                title = "Overview",
                description = "A local view of stock, catalog records, and work waiting to be sent.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("PRODUCTS", activeProducts.toString(), Modifier.weight(1f))
                MetricCard("UNITS ON HAND", units.clean(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("INVENTORY VALUE", currency(value, snapshot.syncState.currency), Modifier.weight(1f))
                MetricCard("LOW STOCK", lowStock.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                Text("SYNC NOTES", style = MaterialTheme.typography.labelMedium, color = Rust, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (snapshot.pendingCount == 0) "No changes are waiting to be sent."
                    else "${snapshot.pendingCount} ${if (snapshot.pendingCount == 1) "change is" else "changes are"} waiting to be sent.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    snapshot.syncState.lastSyncAt?.let { "Last completed ${displayDate(it)}" }
                        ?: "This device has not completed its first refresh.",
                    color = Stone,
                    style = MaterialTheme.typography.bodyMedium,
                )
                snapshot.syncState.lastError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSync, shape = cardShape, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                        Text("Send and receive")
                    }
                    OutlinedButton(onClick = onRefresh, shape = cardShape) { Text("Refresh") }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun InventoryScreen(items: List<InventoryWithPending>, onAdjust: (String, String, Double, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InventoryWithPending?>(null) }
    val filtered = items.filter {
        query.isBlank() || it.productName.contains(query, true) || it.sku.contains(query, true) || it.locationName.contains(query, true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        PageHeading("STOCK BOOK", "Inventory", "Tap a row to record a receipt or count correction.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search products or locations") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            shape = cardShape,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (filtered.isEmpty()) item { EmptyText("No inventory matches this search.") }
            items(filtered, key = { it.id }) { row ->
                InventoryRow(row) { selected = row }
            }
        }
    }

    selected?.let { row ->
        AdjustmentDialog(
            item = row,
            onDismiss = { selected = null },
            onSave = { delta, reason ->
                onAdjust(row.productId, row.locationId, delta, reason)
                selected = null
            },
        )
    }
}

@Composable
private fun InventoryRow(item: InventoryWithPending, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Chalk),
        border = BorderStroke(1.dp, Rule),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.sku} · ${item.locationName}", color = Stone, style = MaterialTheme.typography.bodySmall)
                if (item.pendingDelta != 0.0) {
                    Text(
                        "Includes ${if (item.pendingDelta > 0) "+" else ""}${item.pendingDelta.clean()} queued",
                        color = Rust,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.onHand.clean(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(item.unit, color = Stone, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CatalogScreen(
    products: List<ProductEntity>,
    currencyCode: String,
    onCreate: (ProductDraft) -> Unit,
    onUpdate: (String, ProductDraft) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val filtered = products.filter { query.isBlank() || it.name.contains(query, true) || it.sku.contains(query, true) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        PageHeading("MASTER DATA", "Catalog", "Product records remain readable when the device has no connection.")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search catalog") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = cardShape,
                singleLine = true,
            )
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = Rust,
                contentColor = Chalk,
                shape = cardShape,
            ) { Icon(Icons.Outlined.Add, contentDescription = "Add product") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (filtered.isEmpty()) item { EmptyText("No products match this search.") }
            items(filtered, key = { it.id }) { product ->
                ProductRow(product, currencyCode) { editing = product }
            }
        }
    }

    if (creating) {
        ProductDialog(
            title = "New product",
            product = null,
            onDismiss = { creating = false },
            onSave = { onCreate(it); creating = false },
        )
    }
    editing?.let { product ->
        ProductDialog(
            title = "Edit product",
            product = product,
            onDismiss = { editing = null },
            onSave = { onUpdate(product.id, it); editing = null },
        )
    }
}

@Composable
private fun ProductRow(product: ProductEntity, currencyCode: String, onEdit: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text("${product.sku} · ${product.category}", color = Stone, style = MaterialTheme.typography.bodySmall)
                if (product.localOnly) Text("Waiting to be created on the server", color = Rust, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(currency(product.price, currencyCode), fontWeight = FontWeight.Bold)
                Text(product.unit, color = Stone, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.Edit, null, modifier = Modifier.padding(start = 10.dp).size(18.dp), tint = Stone)
        }
    }
}

@Composable
fun QueueScreen(
    mutations: List<PendingMutationEntity>,
    syncState: SyncStateEntity,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeading("OUTBOX", "Change queue", "Each change keeps the same mutation ID until the server accepts or rejects it.")
            Spacer(Modifier.height(14.dp))
            Button(onClick = onSync, shape = cardShape, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                Text("Send queued changes")
            }
            syncState.lastError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (mutations.isEmpty()) item { EmptyText("The queue is empty.") }
        items(mutations, key = { it.mutationId }) { mutation ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mutation.summary, fontWeight = FontWeight.SemiBold)
                        Text(mutation.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, color = Stone, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(mutation.state.lowercase().replaceFirstChar { it.uppercase() }, color = if (mutation.state == "QUEUED") Stone else Rust, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                mutation.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (mutation.state == "CONFLICT" || mutation.state == "REJECTED") {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onRetry(mutation.mutationId) }, shape = cardShape) { Text("Retry") }
                        TextButton(onClick = { onDismiss(mutation.mutationId) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentDialog(item: InventoryWithPending, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val delta = quantity.toDoubleOrNull()
    val projected = item.onHand + (delta ?: 0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust ${item.productName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current local count: ${item.onHand.clean()} ${item.unit}", color = Stone)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Change in quantity") },
                    supportingText = { Text("Use a negative number to reduce stock.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = cardShape,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    singleLine = true,
                    shape = cardShape,
                )
                if (delta != null) Text("Count after change: ${projected.clean()} ${item.unit}", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            Button(
                enabled = delta != null && delta != 0.0 && projected >= 0,
                onClick = { onSave(delta!!, reason) },
                shape = cardShape,
            ) { Text("Save to queue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = cardShape,
        containerColor = Chalk,
    )
}

@Composable
private fun ProductDialog(title: String, product: ProductEntity?, onDismiss: () -> Unit, onSave: (ProductDraft) -> Unit) {
    var sku by remember { mutableStateOf(product?.sku.orEmpty()) }
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var category by remember { mutableStateOf(product?.category.orEmpty()) }
    var unit by remember { mutableStateOf(product?.unit.orEmpty()) }
    var price by remember { mutableStateOf(product?.price?.clean().orEmpty()) }
    val amount = price.toDoubleOrNull()
    val valid = sku.isNotBlank() && name.isNotBlank() && category.isNotBlank() && unit.isNotBlank() && amount != null && amount >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }, enabled = product == null, singleLine = true, shape = cardShape)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = cardShape)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, shape = cardShape)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, shape = cardShape)
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = cardShape,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(ProductDraft(sku, name, category, unit, amount!!)) },
                shape = cardShape,
            ) { Text("Save to queue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = cardShape,
        containerColor = Chalk,
    )
}

@Composable
private fun PageHeading(eyebrow: String, title: String, description: String) {
    Text(eyebrow, color = Rust, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(4.dp))
    Text(description, color = Stone, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Chalk),
        border = BorderStroke(1.dp, Rule),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = Stone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Chalk),
        border = BorderStroke(1.dp, Rule),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun EmptyText(message: String) {
    Text(message, modifier = Modifier.padding(vertical = 28.dp), color = Stone, style = MaterialTheme.typography.bodyMedium)
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) NumberFormat.getIntegerInstance().format(this) else String.format(Locale.US, "%,.2f", this)

private fun currency(value: Double, code: String): String = try {
    NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(code) }.format(value)
} catch (_: Exception) {
    "$code ${String.format(Locale.US, "%,.2f", value)}"
}

private fun displayDate(value: String): String = try {
    val source = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val target = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    target.format(source.parse(value.take(19)) ?: Date())
} catch (_: Exception) {
    value
}
