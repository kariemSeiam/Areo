package com.pigo.areo.geolink.models

import com.google.android.gms.maps.model.LatLng

data class RouteCache(
    var lastRequestTime: Long,
    var lastLatLng: LatLng,
    var directionResponse: DirectionResponse
)
