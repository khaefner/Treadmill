package com.example.treadmillcontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
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
    onStart: (Float) -> Unit,
    onStop: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetIncline: (Float) -> Unit
) {
    var targetSpeed by remember { mutableFloatStateOf(if (metrics.speedMph > 0f) metrics.speedMph else 1.0f) }
    var targetIncline by remember { mutableFloatStateOf(metrics.inclinePct) }

    val isConnected = connectionState is ConnectionState.Connected
    val isRunning = isConnected && metrics.speedMph > 0.05f

    LaunchedEffect(metrics.speedMph) {
        if (metrics.speedMph > 0f) {
            targetSpeed = (metrics.speedMph * 10).roundToInt() / 10.0f
        }
    }

    LaunchedEffect(metrics.inclinePct) {
        if (metrics.inclinePct >= 0f) {
            targetIncline = (metrics.inclinePct * 2).roundToInt() / 2.0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // 1. Connection Status Card
            ConnectionStatusCard(
                connectionState = connectionState,
                onStartScan = onStartScan,
                onDisconnect = onDisconnect
            )

            // 2. Workout Dashboard / Status Console (Speed, Incline, Elapsed Time, Distance)
            WorkoutConsoleCard(
                metrics = metrics,
                isConnected = isConnected
            )

            // 3. Primary Workout Action Buttons: START & STOP
            WorkoutActionButtons(
                isConnected = isConnected,
                isRunning = isRunning,
                onStart = {
                    if (targetSpeed < 0.5f) {
                        targetSpeed = 1.0f
                    }
                    onStart(targetSpeed)
                },
                onStop = {
                    targetSpeed = 0.0f
                    onStop()
                }
            )

            // 4. Target Speed Adjuster
            ControlCard(
                title = "Target Speed (MPH)",
                value = targetSpeed,
                step = 0.1f,
                range = 0.5f..12.0f,
                presetValues = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.5f, 9.0f),
                icon = Icons.Default.Speed,
                enabled = isConnected,
                onValueChange = { targetSpeed = (it * 10).roundToInt() / 10.0f },
                onValueChangeFinished = {
                    if (isRunning) {
                        onSetSpeed(targetSpeed)
                    }
                },
                onPresetSelected = {
                    targetSpeed = it
                    if (isRunning) {
                        onSetSpeed(it)
                    }
                }
            )

            // 5. Target Incline Adjuster
            ControlCard(
                title = "Target Incline (%)",
                value = targetIncline,
                step = 0.5f,
                range = 0.0f..15.0f,
                presetValues = listOf(0.0f, 1.0f, 2.0f, 3.0f, 5.0f, 8.0f, 10.0f, 12.0f),
                icon = Icons.Default.TrendingUp,
                enabled = isConnected,
                onValueChange = { targetIncline = (it * 2).roundToInt() / 2.0f },
                onValueChangeFinished = {
                    if (isConnected) {
                        onSetIncline(targetIncline)
                    }
                },
                onPresetSelected = {
                    targetIncline = it
                    if (isConnected) {
                        onSetIncline(it)
                    }
                }
            )

            // 6. Live Raw Telemetry Diagnostics
            if (metrics.rawBytesHex.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Live Telemetry Diagnostics",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
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

@Composable
fun WorkoutConsoleCard(
    metrics: TreadmillMetrics,
    isConnected: Boolean
) {
    val isRunning = isConnected && metrics.speedMph > 0.05f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Status Badge + Live Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRunning) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0xFF2E7D32) else Color.Gray)
                    )
                    Text(
                        text = if (isRunning) "RUNNING" else if (isConnected) "READY" else "OFFLINE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF2E7D32) else Color.DarkGray
                    )
                }

                Text(
                    text = "Pace: ${calculatePace(metrics.speedMph)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2x2 Metric Grid: Speed, Incline, Time, Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "SPEED",
                    value = "%.1f".format(metrics.speedMph),
                    unit = "MPH",
                    icon = Icons.Default.Speed
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "INCLINE",
                    value = "%.1f".format(metrics.inclinePct),
                    unit = "% GRADE",
                    icon = Icons.Default.TrendingUp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "TIME",
                    value = formatElapsedTime(metrics.elapsedSeconds),
                    unit = "ELAPSED",
                    icon = Icons.Default.Timer
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "DISTANCE",
                    value = "%.2f".format(metrics.distanceMiles),
                    unit = "MILES",
                    icon = Icons.Default.DirectionsRun
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun WorkoutActionButtons(
    isConnected: Boolean,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // START Button
        Button(
            onClick = onStart,
            enabled = isConnected,
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF81C784).copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Start Workout",
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "START",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // STOP Button
        Button(
            onClick = onStop,
            enabled = isConnected,
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE57373).copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Workout",
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "STOP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
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
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onPresetSelected: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
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
                    },
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                Slider(
                    value = value.coerceIn(range.start, range.endInclusive),
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = range,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val newValue = (value + step).coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                        onValueChangeFinished()
                    },
                    enabled = enabled
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
                            enabled = enabled,
                            onClick = { onPresetSelected(preset) },
                            label = { Text("%.1f".format(preset)) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatElapsedTime(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) {
        "%02d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

private fun calculatePace(speedMph: Float): String {
    if (speedMph < 0.2f) return "--'--\" /mi"
    val paceMinPerMile = 60.0f / speedMph
    val mins = paceMinPerMile.toInt()
    val secs = ((paceMinPerMile - mins) * 60).toInt()
    return "%d'%02d\" /mi".format(mins, secs)
}
