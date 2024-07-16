package com.pigo.areo.utils

import android.content.Context
import com.google.gson.Gson
import com.pigo.areo.ui.current_trip.Trip

object SharedPreferencesUtil {
    private const val TRIPS_PREFS = "trips_prefs"
    private const val CURRENT_TRIP = "current_trip"

    fun saveTrip(context: Context, trip: Trip) {
        val gson = Gson()
        val json = gson.toJson(trip)
        context.getSharedPreferences(TRIPS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CURRENT_TRIP, json)
            .apply()
    }

    fun getTrip(context: Context): Trip? {
        val json = context.getSharedPreferences(TRIPS_PREFS, Context.MODE_PRIVATE)
            .getString(CURRENT_TRIP, null)
        return if (json != null) {
            val gson = Gson()
            gson.fromJson(json, Trip::class.java)
        } else {
            null
        }
    }

    fun clearTrip(context: Context) {
        context.getSharedPreferences(TRIPS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(CURRENT_TRIP)
            .apply()
    }
}
