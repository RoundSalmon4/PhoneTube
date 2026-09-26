package com.roundsalmon4.phonetube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roundsalmon4.phonetube.core.cast.CastConnectionState
import com.roundsalmon4.phonetube.core.cast.CastDevice
import com.roundsalmon4.phonetube.core.cast.CastDiscoveryState

@Composable
fun CastDeviceDialog(
    devices: List<CastDevice>,
    nearbyDevices: List<CastDevice> = emptyList(),
    scanState: CastDiscoveryState = CastDiscoveryState.Idle,
    connectionState: CastConnectionState,
    onConnect: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    onAddDevice: (name: String, host: String, port: Int) -> Unit,
    onRemoveDevice: (host: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8484") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showAddForm) "Add Cast Device" else "Cast to TV") },
        text = {
            if (showAddForm) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Open PhoneTV on your TV and enter the address shown on screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (scanState) {
                        is CastDiscoveryState.Scanning ->
                            if (devices.isEmpty() && nearbyDevices.isEmpty()) {
                                Text(
                                    "Scanning for TVs on this network...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        is CastDiscoveryState.Failed -> Text(
                            scanState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                    if (devices.isEmpty() && nearbyDevices.isEmpty() &&
                        scanState !is CastDiscoveryState.Scanning &&
                        scanState !is CastDiscoveryState.Failed
                    ) {
                        Text(
                            "No cast devices found. Open PhoneTV on your TV to find it automatically, or add it by IP.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    devices.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name.ifBlank { device.host },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                )
                                Text(
                                    text = "${device.host}:${device.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val isConnected =
                                (connectionState as? CastConnectionState.Connected)?.device?.host == device.host
                            if (isConnected) {
                                TextButton(onClick = onDisconnect) {
                                    Text("Disconnect")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onConnect(device) },
                                    enabled = connectionState !is CastConnectionState.Connecting
                                ) {
                                    Text("Cast")
                                }
                            }
                            TextButton(onClick = { onRemoveDevice(device.host) }) {
                                Text("Remove")
                            }
                        }
                    }
                    if (nearbyDevices.isNotEmpty()) {
                        Text(
                            "Nearby",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        nearbyDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${device.host}:${device.port} - discovered",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onConnect(device) },
                                    enabled = connectionState !is CastConnectionState.Connecting
                                ) {
                                    Text("Cast")
                                }
                            }
                        }
                    }
                    when (connectionState) {
                        is CastConnectionState.Connecting -> Text(
                            "Connecting to ${connectionState.device.name}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is CastConnectionState.Connected -> Text(
                            "Connected to ${(connectionState as CastConnectionState.Connected).device.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {
            if (showAddForm) {
                TextButton(
                    onClick = {
                        val portValue = port.toIntOrNull() ?: 8484
                        onAddDevice(name, host, portValue)
                        showAddForm = false
                        name = ""
                        host = ""
                        port = "8484"
                    },
                    enabled = host.isNotBlank()
                ) {
                    Text("Add")
                }
            } else {
                TextButton(onClick = { showAddForm = true }) {
                    Text("Add device")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}