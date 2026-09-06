package com.example.treadmillcontroller.trail

import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.*

object GpxParser {
    private const val TAG = "GpxParser"

    fun parse(inputStream: InputStream): Trail {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var trailName = ""
        var trailDesc = ""

        data class RawPt(val lat: Double, val lon: Double, val ele: Double)
        val rawPoints = mutableListOf<RawPt>()

        var currentTag = ""
        var inPoint = false
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle: Double? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.lowercase()
                    if (currentTag == "trkpt" || currentTag == "rtept") {
                        inPoint = true
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        currentEle = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "name" -> if (!inPoint && trailName.isEmpty()) trailName = text
                            "desc" -> if (!inPoint && trailDesc.isEmpty()) trailDesc = text
                            "ele" -> if (inPoint) currentEle = text.toDoubleOrNull()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "trkpt" || tag == "rtept") {
                        if (currentLat != 0.0 || currentLon != 0.0) {
                            rawPoints.add(RawPt(currentLat, currentLon, currentEle ?: 0.0))
                        }
                        inPoint = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        if (trailName.isEmpty()) {
            trailName = "Custom Hike"
        }

        if (rawPoints.isEmpty()) {
            return Trail(trailName, trailDesc, 0f, 0f, 0f, 0f, emptyList())
        }

        // Calculate cumulative distances
        var cumulativeMeters = 0.0
        val distList = mutableListOf<Double>()
        distList.add(0.0)

        for (i in 1 until rawPoints.size) {
            val pPrev = rawPoints[i - 1]
            val pCurr = rawPoints[i]
            val d = haversineMeters(pPrev.lat, pPrev.lon, pCurr.lat, pCurr.lon)
            cumulativeMeters += d
            distList.add(cumulativeMeters)
        }

        val totalDistMiles = (cumulativeMeters * 0.000621371).toFloat()

        // Calculate elevation statistics and smoothed slope
        var minEle = Double.MAX_VALUE
        var maxEle = Double.MIN_VALUE
        var totalGain = 0.0

        for (i in rawPoints.indices) {
            val ele = rawPoints[i].ele
            if (ele < minEle) minEle = ele
            if (ele > maxEle) maxEle = ele
            if (i > 0 && ele > rawPoints[i - 1].ele) {
                totalGain += (ele - rawPoints[i - 1].ele)
            }
        }

        // Calculate smoothed grade for each point using a lookahead window (~60 meters)
        val trailPoints = mutableListOf<TrailPoint>()
        for (i in rawPoints.indices) {
            val currDist = distList[i]
            val currEle = rawPoints[i].ele

            var targetIdx = i + 1
            while (targetIdx < rawPoints.size && (distList[targetIdx] - currDist) < 60.0) {
                targetIdx++
            }
            if (targetIdx >= rawPoints.size) targetIdx = rawPoints.size - 1

            val grade = if (targetIdx != i) {
                val dD = distList[targetIdx] - currDist
                val dE = rawPoints[targetIdx].ele - currEle
                if (dD > 5.0) {
                    ((dE / dD) * 100.0).toFloat()
                } else {
                    0f
                }
            } else if (i > 0) {
                trailPoints.lastOrNull()?.gradePct ?: 0f
            } else {
                0f
            }

            trailPoints.add(
                TrailPoint(
                    lat = rawPoints[i].lat,
                    lon = rawPoints[i].lon,
                    elevationMeters = currEle,
                    distanceMiles = (currDist * 0.000621371).toFloat(),
                    gradePct = grade.coerceIn(0.0f, 10.0f)
                )
            )
        }

        if (minEle == Double.MAX_VALUE) minEle = 0.0
        if (maxEle == Double.MIN_VALUE) maxEle = 0.0

        return Trail(
            name = trailName,
            description = trailDesc,
            totalDistanceMiles = totalDistMiles,
            totalElevationGainMeters = totalGain.toFloat(),
            minElevationMeters = minEle.toFloat(),
            maxElevationMeters = maxEle.toFloat(),
            points = trailPoints
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    fun loadFromAssets(context: Context, assetPath: String = "hikes/wells_gulch.gpx"): Trail? {
        return try {
            context.assets.open(assetPath).use { parse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading GPX from assets: ${e.message}")
            null
        }
    }
}
