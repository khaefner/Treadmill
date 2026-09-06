package com.example.treadmillcontroller.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.treadmillcontroller.ble.ConnectionState
import com.example.treadmillcontroller.ble.TreadmillMetrics
import com.example.treadmillcontroller.trail.GpxParser
import com.example.treadmillcontroller.trail.Trail
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HikeScreen(
    connectionState: ConnectionState,
    metrics: TreadmillMetrics,
    onStart: (Float) -> Unit,
    onStop: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetIncline: (Float) -> Unit
) {
    val context = LocalContext.current
    val isConnected = connectionState is ConnectionState.Connected
    val isRunning = isConnected && metrics.speedMph > 0.05f

    // Load initial default trail from assets (Well Gulch)
    var trail by remember {
        mutableStateOf(
            GpxParser.loadFromAssets(context, "hikes/wells_gulch.gpx")
                ?: Trail("Well Gulch Nature Trail", "", 0f, 0f, 0f, 0f, emptyList())
        )
    }

    // Hike workout tracking state
    var hikeStartOdometer by remember { mutableFloatStateOf(metrics.distanceMiles) }
    var isHikeActive by remember { mutableStateOf(false) }
    var autoInclineEnabled by remember { mutableStateOf(true) }
    var lastSentIncline by remember { mutableFloatStateOf(-1f) }
    var lastInclineChangeTimeMs by remember { mutableLongStateOf(0L) }

    // Target walking speed on hike screen
    var targetSpeed by remember { mutableFloatStateOf(if (metrics.speedMph > 0f) metrics.speedMph else 2.5f) }

    // File picker launcher for loading custom GPX files
    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val parsed = GpxParser.parse(stream)
                    if (parsed.points.isNotEmpty()) {
                        trail = parsed
                        hikeStartOdometer = metrics.distanceMiles
                        isHikeActive = true
                        lastSentIncline = -1f
                        Toast.makeText(context, "Loaded: ${parsed.name} (${parsed.totalDistanceMiles} mi)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No track points found in GPX file", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load GPX: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Calculate hike distance
    val hikeDistanceMiles = if (isHikeActive) {
        (metrics.distanceMiles - hikeStartOdometer).coerceAtLeast(0f)
    } else {
        0f
    }

    val totalTrailDist = trail.totalDistanceMiles.coerceAtLeast(0.01f)
    val loopCount = (hikeDistanceMiles / totalTrailDist).toInt() + 1
    val distanceInLoop = hikeDistanceMiles % totalTrailDist

    // Target trail incline at current hike position (clamped 0.0 - 10.0%)
    val targetTrailIncline = trail.getTargetIncline(hikeDistanceMiles, loop = true)
    val currentElevationMeters = trail.getElevationAt(hikeDistanceMiles, loop = true)
    val currentElevationFt = (currentElevationMeters * 3.28084).roundToInt()

    // Auto-adjust treadmill incline if enabled, running, and incline differs by at least 0.5%
    LaunchedEffect(hikeDistanceMiles, isRunning, autoInclineEnabled, targetTrailIncline) {
        if (autoInclineEnabled && isRunning && isHikeActive) {
            val now = System.currentTimeMillis()
            val inclineDiff = abs(targetTrailIncline - metrics.inclinePct)
            // Debounce incline commands by at least 3.5s and require a meaningful step (>= 0.45%)
            if (inclineDiff >= 0.45f && (now - lastInclineChangeTimeMs) >= 3500L) {
                lastInclineChangeTimeMs = now
                lastSentIncline = targetTrailIncline
                onSetIncline(targetTrailIncline)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Trail Selection & GPX Loader Card
        TrailHeaderCard(
            trail = trail,
            onLoadGpxClick = {
                gpxPickerLauncher.launch(arrayOf("*/*", "application/gpx+xml", "application/xml", "text/xml"))
            },
            onResetToDefault = {
                val defaultTrail = GpxParser.loadFromAssets(context, "hikes/wells_gulch.gpx")
                if (defaultTrail != null) {
                    trail = defaultTrail
                    hikeStartOdometer = metrics.distanceMiles
                    lastSentIncline = -1f
                    Toast.makeText(context, "Reset to Well Gulch Nature Trail", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // 2. Elevation Profile Canvas Chart
        ElevationProfileChart(
            trail = trail,
            currentDistanceMiles = distanceInLoop
        )

        // 3. Live Hike Status & Auto-Incline Card
        HikeStatusCard(
            targetIncline = targetTrailIncline,
            currentIncline = metrics.inclinePct,
            autoInclineEnabled = autoInclineEnabled,
            onAutoInclineToggle = { autoInclineEnabled = it },
            hikeDistanceMiles = hikeDistanceMiles,
            totalTrailMiles = trail.totalDistanceMiles,
            loopCount = loopCount,
            currentElevationFt = currentElevationFt,
            elevationGainFt = (trail.totalElevationGainMeters * 3.28084).roundToInt()
        )

        // 4. Hike Workout Controls (Start, Pause, Resume, Reset, Stop)
        HikeActionControls(
            isConnected = isConnected,
            isRunning = isRunning,
            isHikeActive = isHikeActive,
            onStartHike = {
                isHikeActive = true
                hikeStartOdometer = metrics.distanceMiles
                lastSentIncline = -1f
                val spd = if (targetSpeed >= 0.5f) targetSpeed else 2.5f
                onStart(spd)
                if (autoInclineEnabled) {
                    onSetIncline(targetTrailIncline)
                }
            },
            onPauseHike = {
                onSetSpeed(0.0f)
            },
            onResumeHike = {
                val spd = if (targetSpeed >= 0.5f) targetSpeed else 2.5f
                onSetSpeed(spd)
            },
            onResetHike = {
                hikeStartOdometer = metrics.distanceMiles
                lastSentIncline = -1f
            },
            onStop = {
                isHikeActive = false
                onStop()
            }
        )

        // 5. Walking Pace & Speed Adjuster
        ControlCard(
            title = "Walking Speed (MPH)",
            value = targetSpeed,
            step = 0.1f,
            range = 0.5f..6.0f,
            presetValues = listOf(1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f),
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
    }
}

@Composable
fun TrailHeaderCard(
    trail: Trail,
    onLoadGpxClick: () -> Unit,
    onResetToDefault: () -> Unit
) {
    val hasElevation = trail.minElevationMeters > 0.01f || trail.maxElevationMeters > 0.01f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = trail.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%.2f mi • +%.0f ft climb".format(
                                trail.totalDistanceMiles,
                                trail.totalElevationGainMeters * 3.28084
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onLoadGpxClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load GPX")
                }
            }

            if (!hasElevation && trail.points.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ This GPX has no <ele> elevation tags. Incline will remain at 0.0%.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (trail.name != "Well Gulch Nature Trail") {
                OutlinedButton(
                    onClick = onResetToDefault,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to Well Gulch", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ElevationProfileChart(
    trail: Trail,
    currentDistanceMiles: Float,
    modifier: Modifier = Modifier
) {
    if (trail.points.isEmpty()) return

    val minEleFt = (trail.minElevationMeters * 3.28084).toFloat()
    val maxEleFt = (trail.maxElevationMeters * 3.28084).toFloat()
    val eleRange = (maxEleFt - minEleFt).coerceAtLeast(30f)
    val totalDist = trail.totalDistanceMiles.coerceAtLeast(0.1f)
    val effectiveDist = currentDistanceMiles.coerceIn(0f, totalDist)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Elevation Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${maxEleFt.roundToInt()} ft max • ${minEleFt.roundToInt()} ft min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height
                val paddingBottom = 16f
                val paddingTop = 16f
                val chartHeight = height - paddingTop - paddingBottom

                val path = Path()
                val fillPath = Path()

                trail.points.forEachIndexed { index, pt ->
                    val x = (pt.distanceMiles / totalDist) * width
                    val eleFt = (pt.elevationMeters * 3.28084).toFloat()
                    val normEle = (eleFt - minEleFt) / eleRange
                    val y = height - paddingBottom - (normEle * chartHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height - paddingBottom)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, height - paddingBottom)
                fillPath.close()

                // Gradient area under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2E7D32).copy(alpha = 0.35f),
                            Color(0xFF81C784).copy(alpha = 0.05f)
                        )
                    )
                )

                // Elevation curve
                drawPath(
                    path = path,
                    color = Color(0xFF2E7D32),
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Current Hiker Position Marker
                val hikerNormX = (effectiveDist / totalDist).coerceIn(0f, 1f)
                val hikerX = hikerNormX * width
                val currEleM = trail.getElevationAt(effectiveDist)
                val currEleFt = currEleM * 3.28084f
                val normCurrEle = (currEleFt - minEleFt) / eleRange
                val hikerY = height - paddingBottom - (normCurrEle * chartHeight)

                // Vertical guideline
                drawLine(
                    color = Color(0xFF1976D2).copy(alpha = 0.6f),
                    start = Offset(hikerX, 0f),
                    end = Offset(hikerX, height - paddingBottom),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )

                // Outer halo and inner pin
                drawCircle(color = Color(0xFF1976D2).copy(alpha = 0.3f), radius = 12f, center = Offset(hikerX, hikerY))
                drawCircle(color = Color.White, radius = 7f, center = Offset(hikerX, hikerY))
                drawCircle(color = Color(0xFF1976D2), radius = 5f, center = Offset(hikerX, hikerY))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.0 mi", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("%.2f mi".format(totalDist / 2f), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("%.2f mi".format(totalDist), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun HikeStatusCard(
    targetIncline: Float,
    currentIncline: Float,
    autoInclineEnabled: Boolean,
    onAutoInclineToggle: (Boolean) -> Unit,
    hikeDistanceMiles: Float,
    totalTrailMiles: Float,
    loopCount: Int,
    currentElevationFt: Int,
    elevationGainFt: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Auto-Incline Switch & Target Incline Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto-Incline Simulation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (autoInclineEnabled) "Automatically matching trail slope" else "Manual incline hold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = autoInclineEnabled,
                    onCheckedChange = onAutoInclineToggle
                )
            }

            // Incline Display Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRAIL SLOPE (TARGET)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "%.1f%% Incline".format(targetIncline),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TREADMILL ACTUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "%.1f%%".format(currentIncline),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress & Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HikeMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "HIKE DISTANCE",
                    value = "%.2f mi".format(hikeDistanceMiles),
                    subtext = "Loop $loopCount (${"%.2f".format(totalTrailMiles)} mi)"
                )

                HikeMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "TRAIL ELEVATION",
                    value = "$currentElevationFt ft",
                    subtext = "+$elevationGainFt ft total gain"
                )
            }
        }
    }
}

@Composable
fun HikeMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtext: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HikeActionControls(
    isConnected: Boolean,
    isRunning: Boolean,
    isHikeActive: Boolean,
    onStartHike: () -> Unit,
    onPauseHike: () -> Unit,
    onResumeHike: () -> Unit,
    onResetHike: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // START / PAUSE / RESUME HIKE Button
        val (btnColor, btnText, btnIcon) = when {
            isHikeActive && isRunning -> Triple(Color(0xFFF57C00), "PAUSE", Icons.Default.Pause)
            isHikeActive && !isRunning -> Triple(Color(0xFF2E7D32), "RESUME", Icons.Default.PlayArrow)
            else -> Triple(Color(0xFF2E7D32), "START HIKE", Icons.Default.PlayArrow)
        }

        Button(
            onClick = {
                when {
                    isHikeActive && isRunning -> onPauseHike()
                    isHikeActive && !isRunning -> onResumeHike()
                    else -> onStartHike()
                }
            },
            enabled = isConnected,
            modifier = Modifier
                .weight(1.2f)
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = btnColor,
                contentColor = Color.White
            )
        ) {
            Icon(btnIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = btnText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // RESET DISTANCE Button
        OutlinedButton(
            onClick = onResetHike,
            modifier = Modifier
                .weight(0.9f)
                .height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset 0 mi", style = MaterialTheme.typography.labelLarge)
        }

        // STOP Button
        Button(
            onClick = onStop,
            enabled = isConnected,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("STOP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
