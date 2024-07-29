package com.pigo.areo.data.remote

import com.pigo.areo.data.model.DirectionResponse
import com.pigo.areo.data.model.ReverseGeocodeResponse
import com.pigo.areo.data.model.TextSearchResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


interface GeolinkService {


    @GET("/reverse_geocode")
    fun reverseGeocode(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("key") apiKey: String
    ): Call<ReverseGeocodeResponse>

    @GET("/directions")
    fun direction(
        @Query("origin_latitude") originLatitude: String,
        @Query("origin_longitude") originLongitude: String,
        @Query("destination_latitude") destinationLatitude: String,
        @Query("destination_longitude") destinationLongitude: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "en"
    ): Call<DirectionResponse>

    @GET("/text_search")
    fun textSearch(
        @Query("query") address: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("key") apiKey: String
    ): Call<TextSearchResponse>
}