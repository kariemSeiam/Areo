package com.pigo.areo.ui.create

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.data.model.ReverseGeocodeResponse
import com.pigo.areo.data.model.SearchResult
import com.pigo.areo.data.remote.api.GeolinkApiService
import com.pigo.areo.shared.SharedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateTripViewModel(
    private val sharedViewModel: SharedViewModel,
    @SuppressLint("StaticFieldLeak") val context: Context
) : ViewModel() {

    private val geolinkApiService = GeolinkApiService()

    val _airportLocation = MutableLiveData<String>()
    val airportLocation: LiveData<String> get() = _airportLocation

    private val _isTripRunning = MutableLiveData<Boolean>()
    val isTripRunning: LiveData<Boolean> get() = _isTripRunning

    private val _selectPilotLocationEvent = MutableLiveData<Unit>()
    val selectPilotLocationEvent: LiveData<Unit> get() = _selectPilotLocationEvent

    private val _selectAirportLocationEvent = MutableLiveData<Unit>()
    val selectAirportLocationEvent: LiveData<Unit> get() = _selectAirportLocationEvent

    private val _airportSearchResults = MutableLiveData<List<SearchResult>>()
    val airportSearchResults: LiveData<List<SearchResult>> get() = _airportSearchResults

    private var airportSearchJob: Job? = null

    init {
        _isTripRunning.value = getSavedTripState()
    }

    fun onRoleChanged(isDriver: Boolean) {
        sharedViewModel.loginAs(if (isDriver) SharedViewModel.UserRole.DRIVER else SharedViewModel.UserRole.PILOT)
    }

    fun toggleTripState() {
        val user = sharedViewModel.userRole.value
        val newState = !(_isTripRunning.value ?: false)
        _isTripRunning.value = newState
        saveTripState(newState)
        if (newState && user == SharedViewModel.UserRole.DRIVER) {
            sharedViewModel.startTrip()
        } else if (user == SharedViewModel.UserRole.DRIVER) {
            sharedViewModel.stopTrip()
            clearLocations()
        }else{
            _isTripRunning.postValue(false)
        }
    }

    private fun clearLocations() {
        sharedViewModel.removeLocation("pilot_location")
        sharedViewModel.removeLocation("airport_location")
        sharedViewModel.removeLocation("driver_location")
    }

    private fun saveTripState(isRunning: Boolean) {
        val sharedPreferences = context.getSharedPreferences("trip_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_trip_running", isRunning).apply()
    }

    private fun getSavedTripState(): Boolean {
        val sharedPreferences = context.getSharedPreferences("trip_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_trip_running", false)
    }

    fun selectAirportLocation() {
        _selectAirportLocationEvent.value = Unit
    }

    fun performAirportLocationSearch(query: String) {
        airportSearchJob?.cancel()
        airportSearchJob = performLocationSearch(query, _airportSearchResults)
    }

    private fun performLocationSearch(
        query: String, resultsLiveData: MutableLiveData<List<SearchResult>>
    ): Job {
        return viewModelScope.launch {
            delay(500)
            sharedViewModel.currentLatLng.value?.let { latLng ->
                geolinkApiService.textSearch(query,
                    latLng.latitude,
                    latLng.longitude,
                    onSuccess = { response -> resultsLiveData.postValue(response.data) },
                    onFailure = { /* Handle error */ })
            }
        }
    }

    fun reverseGeocodeLocation(latLng: LatLng, callback: (ReverseGeocodeResponse) -> Unit) {
        viewModelScope.launch {
            geolinkApiService.reverseGeocode(latLng.latitude, latLng.longitude).collect { result ->
                result.onSuccess { response -> callback(response) }
                    .onFailure { /* Handle failure */ }
            }
        }
    }
}
