package com.pigo.areo.utils

import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val USER_ROLE = stringPreferencesKey("user_role")
    val CURRENT_TRIP = stringPreferencesKey("current_trip")
    val LAST_FETCHED_LAT = stringPreferencesKey("last_fetched_lat")
    val LAST_FETCHED_LNG = stringPreferencesKey("last_fetched_lng")
}