package com.pigo.areo.geolink.models

import com.google.android.gms.maps.model.LatLng

data class RouteCache(
    val lastRequestTime: Long,
    val lastLatLng: LatLng,
    val directionResponse: DirectionResponse
)

