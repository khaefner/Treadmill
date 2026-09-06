package com.example.treadmillcontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.treadmillcontroller.ble.ConnectionState
import com.example.treadmillcontroller.ble.TreadmillMetrics
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreadmillScreen(
    connectionState: ConnectionState,
    metrics: TreadmillMetrics,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetIncline: (Float) -> Unit
) {
    var targetSpeed by remember { mutableFloatStateOf(metrics.speedMph) }
    var targetIncline by remember { mutableFloatStateOf(metrics.inclinePct) }

    LaunchedEffect(metrics.speedMph) {
        if (metrics.speedMph > 0f) {
            targetSpeed = metrics.speedMph
        }
    }

    LaunchedEffect(metrics.inclinePct) {
        if (metrics.inclinePct >= 0f) {
            targetIncline = metrics.inclinePct
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treadmill Controller", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusCard(
                connectionState = connectionState,
                onStartScan = onStartScan,
                onDisconnect = onDisconnect
            )

            ControlCard(
                title = "Speed (MPH)",
                value = targetSpeed,
                step = 0.1f,
                range = 0.5f..12.0f,
                presetValues = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.5f, 9.0f),
                icon = Icons.Default.Speed,
                onValueChange = { targetSpeed = (it * 10).roundToInt() / 10.0f },
                onValueChangeFinished = { onSetSpeed(targetSpeed) },
                onPresetSelected = {
                    targetSpeed = it
                    onSetSpeed(it)
                }
            )

            ControlCard(
                title = "Incline (%)",
                value = targetIncline,
                step = 0.5f,
                range = 0.0f..15.0f,
                presetValues = listOf(0.0f, 1.0f, 2.0f, 3.0f, 5.0f, 8.0f, 10.0f, 12.0f),
                icon = Icons.Default.TrendingUp,
                onValueChange = { targetIncline = (it * 2).roundToInt() / 2.0f },
                onValueChangeFinished = { onSetIncline(targetIncline) },
                onPresetSelected = {
                    targetIncline = it
                    onSetIncline(it)
                }
            )

            if (metrics.rawBytesHex.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Live Telemetry Frame",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speed: ${"%.1f".format(metrics.speedMph)} MPH | Incline: ${"%.1f".format(metrics.inclinePct)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val minutes = metrics.elapsedSeconds / 60
                        val seconds = metrics.elapsedSeconds % 60
                        Text(
                            text = "Distance: ${"%.2f".format(metrics.distanceMiles)} mi | Time: %02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = metrics.rawBytesHex,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (connectionState) {
                    is ConnectionState.Connected -> Icons.Default.BluetoothConnected
                    is ConnectionState.Scanning -> Icons.Default.BluetoothSearching
                    else -> Icons.Default.Bluetooth
                }
                val iconTint = if (connectionState is ConnectionState.Connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Gray
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )

                Column {
                    val statusText = when (connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.Scanning -> "Scanning for Treadmill..."
                        is ConnectionState.Disconnected -> "Disconnected"
                        is ConnectionState.Error -> "Connection Error"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (connectionState is ConnectionState.Error) {
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (connectionState is ConnectionState.Connected) {
                        onDisconnect()
                    } else {
                        onStartScan()
                    }
                },
                enabled = connectionState !is ConnectionState.Connecting && connectionState !is ConnectionState.Scanning
            ) {
                Text(if (connectionState is ConnectionState.Connected) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
fun ControlCard(
    title: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    presetValues: List<Float>,
    icon: ImageVector,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onPresetSelected: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null)
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "%.1f".format(value),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                        onValueChangeFinished()
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = range,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val newValue = (value + step).coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                        onValueChangeFinished()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetValues) { preset ->
                        val isSelected = Math.abs(preset - value) < 0.05f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPresetSelected(preset) },
                            label = { Text("%.1f".format(preset)) }
                        )
                    }
                }
            }
        }
    }
}
