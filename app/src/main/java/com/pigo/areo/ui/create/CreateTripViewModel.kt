package com.pigo.areo.ui.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.geolink.GeolinkApiService
import com.pigo.areo.geolink.models.ReverseGeocodeResponse
import com.pigo.areo.geolink.models.SearchResult
import com.pigo.areo.shared.SharedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateTripViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val geolinkApiService = GeolinkApiService()

    private val _pilotLocation = MutableLiveData<String>()
    val pilotLocation: LiveData<String> get() = _pilotLocation

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
        observeCurrentLocation()
    }



    private fun observeCurrentLocation() {

        sharedViewModel.pilotLatLng?.let {
            reverseGeocodeLocation(it) { address ->
                _pilotLocation.value = address.data.address
            }
        }

    }

    fun onRoleChanged(isDriver: Boolean) {
        sharedViewModel.loginAs(if (isDriver) SharedViewModel.UserRole.DRIVER else SharedViewModel.UserRole.PILOT)
    }

    fun toggleTripState() {
        val newState = !(_isTripRunning.value ?: false)
        _isTripRunning.value = newState
        if (newState) {
            sharedViewModel.startTrip()
        } else {
            sharedViewModel.stopTrip()
            sharedViewModel.removeLocation("pilot_location")
            sharedViewModel.removeLocation("airport_location")
            sharedViewModel.removeLocation("driver_location")
        }
    }


    fun selectAirportLocation() {
        _selectAirportLocationEvent.value = Unit
    }

    fun performAirportLocationSearch(query: String) {
        airportSearchJob?.cancel()
        airportSearchJob = performLocationSearch(query, _airportSearchResults)
    }

    private fun performLocationSearch(query: String, resultsLiveData: MutableLiveData<List<SearchResult>>): Job {
        return viewModelScope.launch {
            delay(500)
            sharedViewModel.currentLatLng.value?.let { latLng ->
                geolinkApiService.textSearch(query, latLng.latitude, latLng.longitude,
                    onSuccess = { response -> resultsLiveData.postValue(response.data) },
                    onFailure = { /* Handle error */ })
            }
        }
    }

    fun reverseGeocodeLocation(latLng: LatLng, callback: (ReverseGeocodeResponse) -> Unit) {
        viewModelScope.launch {
            geolinkApiService.reverseGeocode(latLng.latitude, latLng.longitude).collect { result ->
                result.onSuccess { response ->
                    callback(response)
                }.onFailure { /* Handle failure */ }
            }
        }
    }
}
