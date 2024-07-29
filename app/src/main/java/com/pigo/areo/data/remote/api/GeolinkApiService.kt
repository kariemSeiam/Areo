package com.pigo.areo.data.remote.api

import com.pigo.areo.data.model.DirectionResponse
import com.pigo.areo.data.model.ReverseGeocodeResponse
import com.pigo.areo.data.model.TextSearchResponse
import com.pigo.areo.data.remote.GeolinkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class GeolinkApiService {

    private val geolinkService: GeolinkService
    private var apiLink: String
    private var apiKey: String

    init {
        val (link, key) = "https://geolink.onrender.com" to "eea98fb2-8bc4-47f6-a2e0-3567cdafa4a9"
        apiLink = link
        apiKey = key

        val retrofit =
            Retrofit.Builder().baseUrl(apiLink).addConverterFactory(GsonConverterFactory.create())
                .build()

        geolinkService = retrofit.create(GeolinkService::class.java)
    }

    fun reverseGeocode(
        latitude: Double, longitude: Double
    ): Flow<Result<ReverseGeocodeResponse>> = flow {
        try {
            val response = geolinkService.reverseGeocode(latitude, longitude, apiKey).execute()
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.success) {
                    emit(Result.success(responseBody))
                } else {
                    emit(Result.failure(IOException("Unsuccessful response or response body is null or success is false")))
                }
            } else {
                emit(Result.failure(IOException("Error: ${response.code()} ${response.message()}")))
            }
        } catch (e: IOException) {
            emit(Result.failure(e))
        } catch (e: HttpException) {
            emit(Result.failure(IOException("HTTP error: ${e.code()} ${e.message()}")))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun direction(
        originLatitude: String,
        originLongitude: String,
        destinationLatitude: String,
        destinationLongitude: String,
    ): Flow<Result<DirectionResponse>> = flow {
        try {
            // Make the network call
            val response = geolinkService.direction(
                originLatitude, originLongitude, destinationLatitude, destinationLongitude, apiKey
            ).execute()

            // Check if the response is successful
            if (response.isSuccessful) {
                val directionResponse = response.body()
                // Check if response body is not null
                if (directionResponse != null) {
                    emit(Result.success(directionResponse))
                } else {
                    emit(Result.failure(IOException("Direction response body is null")))
                }
            } else {
                emit(Result.failure(IOException("Error: ${response.code()} ${response.message()}")))
            }
        } catch (e: IOException) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun textSearch(
        query: String,
        latitude: Double,
        longitude: Double,
        onSuccess: (TextSearchResponse) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val call = geolinkService.textSearch(query, latitude, longitude, apiKey)
        call.enqueue(object : Callback<TextSearchResponse> {
            override fun onResponse(
                call: Call<TextSearchResponse>, response: Response<TextSearchResponse>
            ) {
                if (response.isSuccessful) {
                    onSuccess(response.body()!!)
                } else {
                    onFailure("Error: ${response.code()} ${response.message()}")
                }
            }

            override fun onFailure(call: Call<TextSearchResponse>, t: Throwable) {
                onFailure("Error: ${t.message}")
            }
        })
    }


}
