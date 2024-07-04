package com.pigo.areo.geolink.models

import com.google.gson.annotations.SerializedName

data class ReverseGeocodeResponse(
    @SerializedName("data") val data: ReverseGeocodeData,
    @SerializedName("success") val success: Boolean
)

data class ReverseGeocodeData(
    @SerializedName("address") val address: String,
    @SerializedName("sub_address") val subAddress: String
)
