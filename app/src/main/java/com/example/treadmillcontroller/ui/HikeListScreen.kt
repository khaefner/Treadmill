package com.example.treadmillcontroller.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.treadmillcontroller.trail.Trail

@Composable
fun HikeListScreen(
    availableTrails: List<Trail>,
    activeTrail: Trail?,
    isHikeRunning: Boolean,
    activeHikeDistanceMiles: Float,
    onSelectTrail: (Trail) -> Unit,
    onImportGpx: (Uri) -> Unit,
    onDeleteTrail: (Trail) -> Unit,
    onViewActiveHike: () -> Unit
) {
    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportGpx(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // 1. Add Hike Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Custom Hike",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Import any .gpx file from device storage to simulate its elevation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            gpxPickerLauncher.launch(
                                arrayOf("*/*", "application/gpx+xml", "application/xml", "text/xml")
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Hike")
                    }
                }
            }
        }

        // 2. Active Hike In-Progress Banner (if workout is active)
        if (activeTrail != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewActiveHike() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHikeRunning) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                    ),
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isHikeRunning) Color(0xFF2E7D32) else Color.Gray)
                            )
                            Column {
                                Text(
                                    text = if (isHikeRunning) "HIKE IN PROGRESS" else "SELECTED HIKE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHikeRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = activeTrail.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "%.2f mi completed • Loop %d".format(
                                        activeHikeDistanceMiles,
                                        (activeHikeDistanceMiles / activeTrail.totalDistanceMiles.coerceAtLeast(0.01f)).toInt() + 1
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onViewActiveHike,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHikeRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isHikeRunning) "Resume" else "View")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Available Hikes (${availableTrails.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 3. Trail Cards
        items(availableTrails, key = { it.id }) { trail ->
            val isActive = activeTrail?.id == trail.id
            TrailItemCard(
                trail = trail,
                isActive = isActive,
                onSelect = { onSelectTrail(trail) },
                onDelete = { onDeleteTrail(trail) }
            )
        }
    }
}

@Composable
fun TrailItemCard(
    trail: Trail,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Trail") },
            text = { Text("Are you sure you want to remove '${trail.name}' from your available hikes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
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
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = trail.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (!trail.isBuiltIn) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Stats chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HikeStatChip(label = "Distance", value = "%.2f mi".format(trail.totalDistanceMiles))
                HikeStatChip(label = "Gain", value = "+%.0f ft".format(trail.totalElevationGainMeters * 3.28084))
                HikeStatChip(
                    label = "Range",
                    value = "%.0f - %.0f ft".format(
                        trail.minElevationMeters * 3.28084,
                        trail.maxElevationMeters * 3.28084
                    )
                )
            }

            // Mini elevation curve thumbnail
            if (trail.points.isNotEmpty()) {
                MiniElevationPreview(
                    trail = trail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }

            // Select / Start Button
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Visibility else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isActive) "View Active Hike" else "Select & Start Hike")
            }
        }
    }
}

@Composable
fun MiniElevationPreview(
    trail: Trail,
    modifier: Modifier = Modifier
) {
    val minEle = trail.minElevationMeters
    val maxEle = trail.maxElevationMeters
    val eleRange = (maxEle - minEle).coerceAtLeast(10f)
    val totalDist = trail.totalDistanceMiles.coerceAtLeast(0.1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val path = Path()
        val fillPath = Path()

        trail.points.forEachIndexed { index, pt ->
            val x = (pt.distanceMiles / totalDist) * width
            val normEle = ((pt.elevationMeters - minEle) / eleRange).toFloat()
            val y = height - (normEle * (height - 8f)) - 4f

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2E7D32).copy(alpha = 0.25f),
                    Color(0xFF2E7D32).copy(alpha = 0.02f)
                )
            )
        )

        drawPath(
            path = path,
            color = Color(0xFF2E7D32),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun HikeStatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "$label:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
