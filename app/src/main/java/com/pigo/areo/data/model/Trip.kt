package com.pigo.areo.data.model

import com.google.android.gms.maps.model.LatLng


data class Trip(
    val tripId: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val coordinates: List<CustomLatLng> = emptyList(),
    val speeds: List<Float> = emptyList(),
    val startTrip: Boolean = false,
    val arrivedAtPilot: Boolean = false,
    val arrivedAtAirport: Boolean = false
)


data class CustomLatLng(
    val latitude: Double = 0.0, val longitude: Double = 0.0
) {
    fun toLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }

    companion object {
        fun fromLatLng(latLng: LatLng): CustomLatLng {
            return CustomLatLng(latLng.latitude, latLng.longitude)
        }
    }
}
