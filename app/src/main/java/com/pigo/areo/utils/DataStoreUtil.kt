package com.pigo.areo.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.pigo.areo.data.model.Trip
import com.pigo.areo.shared.SharedViewModel.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object DataStoreUtil {
    val Context.dataStore by preferencesDataStore(name = "user_prefs")
    private val gson = Gson()


    fun getUserRoleFlow(context: Context): Flow<UserRole?> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.USER_ROLE]?.let { UserRole.valueOf(it) }
        }
    }


    suspend fun saveTrip(context: Context, trip: Trip) {
        val json = gson.toJson(trip)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_TRIP] = json
        }
    }

    fun getTripFlow(context: Context): Flow<Trip?> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[PreferencesKeys.CURRENT_TRIP]
            json?.let { gson.fromJson(it, Trip::class.java) }
        }
    }

    suspend fun saveLastFetchedLatLng(context: Context, latLng: LatLng) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_FETCHED_LAT] = latLng.latitude.toString()
            preferences[PreferencesKeys.LAST_FETCHED_LNG] = latLng.longitude.toString()
        }
    }

    fun getLastFetchedLatLngFlow(context: Context): Flow<LatLng> {
        return context.dataStore.data.map { preferences ->
            val lat = preferences[PreferencesKeys.LAST_FETCHED_LAT]?.toDoubleOrNull() ?: 0.0
            val lng = preferences[PreferencesKeys.LAST_FETCHED_LNG]?.toDoubleOrNull() ?: 0.0
            LatLng(lat, lng)
        }
    }

    suspend fun clearTrip(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CURRENT_TRIP)
        }
    }
}
