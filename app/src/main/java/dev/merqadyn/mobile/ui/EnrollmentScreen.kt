package dev.merqadyn.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.merqadyn.mobile.BuildConfig
import dev.merqadyn.mobile.R
import dev.merqadyn.mobile.ui.theme.Chalk
import dev.merqadyn.mobile.ui.theme.Ink
import dev.merqadyn.mobile.ui.theme.Paper
import dev.merqadyn.mobile.ui.theme.Rust
import dev.merqadyn.mobile.ui.theme.Stone

@Composable
fun EnrollmentScreen(
    busy: Boolean,
    notice: String?,
    onClearNotice: () -> Unit,
    onEnroll: (String, String, String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf(BuildConfig.API_BASE_URL) }
    var deviceId by remember { mutableStateOf(BuildConfig.DEVICE_ID) }
    var code by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onClearNotice()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_merqadyn_foreground),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text("MERQADYN", color = Chalk, fontWeight = FontWeight.Black)
                Text("Phone enrollment", color = Chalk.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Connect this phone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Run scripts/configure-local.ps1 on your computer. It creates a short, one-time code without copying an administrator password into the app.",
                color = Stone,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("API address") },
                supportingText = { Text("Use HTTPS outside local development.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(3.dp),
            )
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("Device ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(3.dp),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(10) },
                label = { Text("10-character enrollment code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(3.dp),
            )
            Button(
                onClick = { onEnroll(serverUrl, deviceId, code) },
                enabled = !busy && serverUrl.isNotBlank() && deviceId.isNotBlank() && code.length == 10,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(3.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Rust, strokeWidth = 2.dp)
                } else {
                    Text("Enroll this phone")
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.padding(16.dp))
    }
}
