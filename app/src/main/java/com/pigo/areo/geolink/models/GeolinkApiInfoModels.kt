package com.pigo.areo.geolink.models

data class GeolinkApiInfoResponse(
    val code: String, val message: String, val data: GeolinkApiData?
)

data class GeolinkApiData(
    val api: String, val key: String
)
