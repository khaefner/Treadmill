package com.example.treadmillcontroller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.treadmillcontroller.ble.ConnectionState
import com.example.treadmillcontroller.ble.TreadmillMetrics
import com.example.treadmillcontroller.trail.Trail
import com.example.treadmillcontroller.trail.TrailRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    connectionState: ConnectionState,
    metrics: TreadmillMetrics,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: (Float) -> Unit,
    onStop: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetIncline: (Float) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val trailRepository = remember { TrailRepository(context) }
    val availableTrails by trailRepository.trails.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        trailRepository.initialize()
    }

    var selectedTrail by remember { mutableStateOf<Trail?>(null) }
    var activeTrail by remember { mutableStateOf<Trail?>(null) }
    var isHikeActive by remember { mutableStateOf(false) }
    var hikeStartOdometer by remember { mutableFloatStateOf(0f) }

    val isConnected = connectionState is ConnectionState.Connected
    val isRunning = isConnected && metrics.speedMph > 0.05f

    // If treadmill distance resets or decreases below start point, synchronize it
    LaunchedEffect(metrics.distanceMiles) {
        if (metrics.distanceMiles < hikeStartOdometer) {
            hikeStartOdometer = metrics.distanceMiles
        }
    }

    // Intercept system back button when viewing an active hike to return to the trail library
    BackHandler(enabled = selectedTab == 1 && selectedTrail != null) {
        selectedTrail = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Treadmill Controller",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        when (connectionState) {
                            is ConnectionState.Connected -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E7D32))
                                        )
                                        Text(
                                            text = "Connected",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                            is ConnectionState.Scanning, ConnectionState.Connecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 12.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = onStartScan,
                                    modifier = Modifier.padding(end = 12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Manual Console") },
                        icon = { Icon(Icons.Default.Speed, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val hikeIndicator = if (isHikeActive && isRunning) " • Running" else ""
                            Text("Trail Hike$hikeIndicator")
                        },
                        icon = { Icon(Icons.Default.Landscape, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                TreadmillScreen(
                    connectionState = connectionState,
                    metrics = metrics,
                    onStartScan = onStartScan,
                    onDisconnect = onDisconnect,
                    onStart = onStart,
                    onStop = onStop,
                    onSetSpeed = onSetSpeed,
                    onSetIncline = onSetIncline
                )
            } else {
                val currentTrail = selectedTrail
                if (currentTrail != null) {
                    HikeScreen(
                        trail = currentTrail,
                        onBackToHikesList = { selectedTrail = null },
                        connectionState = connectionState,
                        metrics = metrics,
                        isHikeActive = isHikeActive && activeTrail?.id == currentTrail.id,
                        hikeStartOdometer = hikeStartOdometer,
                        onStartHike = { speed ->
                            activeTrail = currentTrail
                            isHikeActive = true
                            hikeStartOdometer = metrics.distanceMiles
                            onStart(speed)
                        },
                        onPauseHike = {
                            onStop()
                        },
                        onResumeHike = { speed ->
                            if (activeTrail?.id != currentTrail.id) {
                                activeTrail = currentTrail
                                hikeStartOdometer = metrics.distanceMiles
                            }
                            isHikeActive = true
                            onStart(speed)
                        },
                        onResetHike = {
                            hikeStartOdometer = metrics.distanceMiles
                        },
                        onStop = {
                            isHikeActive = false
                            onStop()
                        },
                        onSetSpeed = onSetSpeed,
                        onSetIncline = onSetIncline
                    )
                } else {
                    val hikeDist = if (isHikeActive) (metrics.distanceMiles - hikeStartOdometer).coerceAtLeast(0f) else 0f
                    HikeListScreen(
                        availableTrails = availableTrails,
                        activeTrail = if (isHikeActive) activeTrail else null,
                        isHikeRunning = isHikeActive && isRunning,
                        activeHikeDistanceMiles = hikeDist,
                        onSelectTrail = { trail ->
                            selectedTrail = trail
                        },
                        onImportGpx = { uri ->
                            coroutineScope.launch {
                                val result = trailRepository.addTrailFromUri(uri)
                                result.onSuccess { imported ->
                                    snackbarHostState.showSnackbar("Added '${imported.name}'")
                                    selectedTrail = imported
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Error adding trail: ${err.message ?: "Invalid GPX"}")
                                }
                            }
                        },
                        onDeleteTrail = { trail ->
                            coroutineScope.launch {
                                if (activeTrail?.id == trail.id) {
                                    activeTrail = null
                                    isHikeActive = false
                                }
                                if (selectedTrail?.id == trail.id) {
                                    selectedTrail = null
                                }
                                val deleted = trailRepository.deleteTrail(trail)
                                if (deleted) {
                                    snackbarHostState.showSnackbar("Removed '${trail.name}'")
                                }
                            }
                        },
                        onViewActiveHike = {
                            if (activeTrail != null) {
                                selectedTrail = activeTrail
                            }
                        }
                    )
                }
            }
        }
    }
}
