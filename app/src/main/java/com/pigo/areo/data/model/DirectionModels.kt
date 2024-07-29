package com.pigo.areo.data.model

import com.google.gson.annotations.SerializedName

data class DirectionResponse(
    @SerializedName("success") val success: Boolean, @SerializedName("data") val data: List<Route>
)

data class Route(
    @SerializedName("distance_meters") val distanceMeters: Int,
    @SerializedName("distance_text") val distanceText: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("duration_text") val durationText: String,
    @SerializedName("waypoints") val waypoints: List<Waypoint>
)

data class Waypoint(
    @SerializedName("lat") val lat: Double, @SerializedName("lng") val lng: Double
)

fun findShortestPath(routes: List<Route>): Route? {
    return routes.minByOrNull { route ->
        // Define weights for distance and duration (time)
        val distanceWeight = 0.25 // You can adjust these weights based on your preference
        val durationWeight = 0.75 // 0.75 means equal importance, adjust as needed

        // Calculate combined score using weighted sum
        val combinedScore =
            (route.distanceMeters * distanceWeight) + (route.durationSeconds * durationWeight)

        // Return the route with the minimum combined score
        combinedScore
    }
}