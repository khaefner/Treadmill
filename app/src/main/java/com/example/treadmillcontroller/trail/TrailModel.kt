package com.example.treadmillcontroller.trail

import kotlin.math.roundToInt

data class TrailPoint(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double,
    val distanceMiles: Float,
    val gradePct: Float // raw calculated slope (smoothed)
)

data class Trail(
    val name: String,
    val description: String,
    val totalDistanceMiles: Float,
    val totalElevationGainMeters: Float,
    val minElevationMeters: Float,
    val maxElevationMeters: Float,
    val points: List<TrailPoint>
) {
    /**
     * Calculates the target treadmill incline grade (0.0 to 10.0%) for a given workout distance in miles.
     * If loop is enabled, wraps distance around totalDistanceMiles.
     * Rounds to nearest 0.5% (standard treadmill incline interval).
     */
    fun getTargetIncline(distanceMiles: Float, loop: Boolean = true): Float {
        if (points.isEmpty()) return 0.0f
        if (totalDistanceMiles <= 0.001f) return 0.0f

        val effectiveDist = if (loop) {
            val rem = distanceMiles % totalDistanceMiles
            if (rem < 0f) rem + totalDistanceMiles else rem
        } else {
            distanceMiles.coerceIn(0.0f, totalDistanceMiles)
        }

        val idx = points.indexOfFirst { it.distanceMiles >= effectiveDist }
        val rawGrade = when {
            idx <= 0 -> points.first().gradePct
            idx >= points.size -> points.last().gradePct
            else -> {
                val p0 = points[idx - 1]
                val p1 = points[idx]
                val span = p1.distanceMiles - p0.distanceMiles
                if (span > 0.0001f) {
                    val ratio = (effectiveDist - p0.distanceMiles) / span
                    p0.gradePct + ratio * (p1.gradePct - p0.gradePct)
                } else {
                    p0.gradePct
                }
            }
        }

        // Clamp within 0.0% to 10.0% as required by the user
        val clamped = rawGrade.coerceIn(0.0f, 10.0f)
        // Round to nearest 0.5%
        return (clamped * 2f).roundToInt() / 2.0f
    }

    /**
     * Returns the elevation in meters at a given distance along the trail.
     */
    fun getElevationAt(distanceMiles: Float, loop: Boolean = true): Float {
        if (points.isEmpty()) return 0.0f
        if (totalDistanceMiles <= 0.001f) return points.first().elevationMeters.toFloat()

        val effectiveDist = if (loop) {
            val rem = distanceMiles % totalDistanceMiles
            if (rem < 0f) rem + totalDistanceMiles else rem
        } else {
            distanceMiles.coerceIn(0.0f, totalDistanceMiles)
        }

        val idx = points.indexOfFirst { it.distanceMiles >= effectiveDist }
        return when {
            idx <= 0 -> points.first().elevationMeters.toFloat()
            idx >= points.size -> points.last().elevationMeters.toFloat()
            else -> {
                val p0 = points[idx - 1]
                val p1 = points[idx]
                val span = p1.distanceMiles - p0.distanceMiles
                if (span > 0.0001f) {
                    val ratio = (effectiveDist - p0.distanceMiles) / span
                    (p0.elevationMeters + ratio * (p1.elevationMeters - p0.elevationMeters)).toFloat()
                } else {
                    p0.elevationMeters.toFloat()
                }
            }
        }
    }
}
