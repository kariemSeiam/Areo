package com.pigo.areo.ui.current_trip

import com.google.android.gms.maps.model.LatLng

data class Trip(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val coordinates: List<LatLng>,
    val speeds: List<Float>,
    val startTrip: Boolean = false,
    val arrivedAtPilot: Boolean = false,
    val arrivedAtAirport: Boolean = false
)

