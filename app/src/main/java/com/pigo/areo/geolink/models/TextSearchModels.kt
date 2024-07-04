package com.pigo.areo.geolink.models

import com.google.gson.annotations.SerializedName

data class TextSearchResponse(
    @SerializedName("data") val data: List<SearchResult>,
    @SerializedName("success") val success: Boolean
)

data class SearchResult(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("long_address") val longAddress: String?,
    @SerializedName("short_address") val shortAddress: String?
)
