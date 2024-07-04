package com.pigo.areo.geolink.models

import com.google.gson.annotations.SerializedName

data class GeocodeResponse(
    @SerializedName("data") val data: GeocodeData,
    @SerializedName("success") val success: Boolean
)

data class GeocodeData(
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lng: Double,
    @SerializedName("long_address") val longAddress: String,
    @SerializedName("short_address") val shortAddress: String
)
