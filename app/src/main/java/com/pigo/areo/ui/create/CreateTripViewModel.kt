package com.pigo.areo.ui.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.geolink.GeolinkApiService
import com.pigo.areo.geolink.models.SearchResult
import com.pigo.areo.shared.SharedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateTripViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _isDriver = MutableLiveData<Boolean>()
    val isDriver: LiveData<Boolean> get() = _isDriver

    private val _pilotLocation = MutableLiveData<String>()
    val pilotLocation: LiveData<String> get() = _pilotLocation

    private val _airportLocation = MutableLiveData<String>()
    val airportLocation: LiveData<String> get() = _airportLocation

    private val _isTripRunning = MutableLiveData<Boolean>()
    val isTripRunning: LiveData<Boolean> get() = _isTripRunning

    private val _selectPilotLocationEvent = MutableLiveData<Unit>()
    val selectPilotLocationEvent: LiveData<Unit> get() = _selectPilotLocationEvent

    private val _selectAirportLocationEvent = MutableLiveData<Unit>()
    val selectAirportLocationEvent: LiveData<Unit> get() = _selectAirportLocationEvent

    private val _pilotSearchResults = MutableLiveData<List<SearchResult>>()
    val pilotSearchResults: LiveData<List<SearchResult>> get() = _pilotSearchResults

    private val _airportSearchResults = MutableLiveData<List<SearchResult>>()
    val airportSearchResults: LiveData<List<SearchResult>> get() = _airportSearchResults

    private val geolinkApiService = GeolinkApiService()

    private var pilotSearchJob: Job? = null
    private var airportSearchJob: Job? = null

    init {
        sharedViewModel.userRole.observeForever { userRole ->
            _isDriver.value = (userRole == SharedViewModel.UserRole.DRIVER)
            if (_isDriver.value == true) {
                sharedViewModel.driverLatLng?.let {
                    reverseGeocodeLocation(it) { address ->
                        _airportLocation.value = address
                    }
                }
            } else {
                sharedViewModel.pilotLatLng?.let { latLng ->
                    reverseGeocodeLocation(latLng) { address ->
                        _pilotLocation.value = address
                    }
                }
            }
        }

        // Observe changes in location to notify when driver/pilot arrives at the location
        sharedViewModel.currentLatLng.observeForever { currentLatLng ->
            if (_isDriver.value == true) {
                sharedViewModel.pilotLatLng?.let { pilotLatLng ->
                    if (isLocationArrived(currentLatLng, pilotLatLng)) {
                        // Notify that driver has arrived at the pilot's location
                    }
                }
            } else {
                sharedViewModel.driverLatLng?.let { driverLatLng ->
                    if (isLocationArrived(currentLatLng, driverLatLng)) {
                        // Notify that pilot has arrived at the driver's location
                    }
                }
            }
        }
    }

    fun onRoleChanged(isDriver: Boolean) {
        _isDriver.value = isDriver
        sharedViewModel.switchUserRole()
    }

    fun updatePilotLocation(latLng: LatLng) {
        sharedViewModel.updatePilotLatLng(latLng)
    }

    fun updateAirportLocation(latLng: LatLng) {
        sharedViewModel.updateDriverLatLng(latLng)
    }

    fun toggleTripState() {
        _isTripRunning.value = !(_isTripRunning.value ?: false)
        if (_isTripRunning.value == true) {
            sharedViewModel.startLocationUpdates()
        } else {
            sharedViewModel.removeLocation("pilot_location")
            sharedViewModel.removeLocation("airport_location")
        }
    }

    fun selectPilotLocation() {
        _selectPilotLocationEvent.value = Unit
    }

    fun selectAirportLocation() {
        _selectAirportLocationEvent.value = Unit
    }

    fun confirmPilotLocation() {
        sharedViewModel.pilotLatLng?.let {
            sharedViewModel.updateLocation("pilot_location", GeoLocation(it.latitude, it.longitude))
        }
    }

    fun confirmAirportLocation() {
        sharedViewModel.driverLatLng?.let {
            sharedViewModel.updateLocation("airport_location", GeoLocation(it.latitude, it.longitude))
        }
    }

    fun performPilotLocationSearch(query: String) {
        pilotSearchJob?.cancel()
        pilotSearchJob = viewModelScope.launch {
            delay(2000) // 2 seconds delay
            sharedViewModel.currentLatLng.value?.let { latLng ->
                geolinkApiService.textSearch(
                    query,
                    latLng.latitude,
                    latLng.longitude,
                    onSuccess = { response ->
                        _pilotSearchResults.postValue(response.data)
                    },
                    onFailure = { error ->
                        // Handle error
                    })
            }
        }
    }

    fun performAirportLocationSearch(query: String) {
        airportSearchJob?.cancel()
        airportSearchJob = viewModelScope.launch {
            delay(2000) // 2 seconds delay
            sharedViewModel.currentLatLng.value?.let { latLng ->
                geolinkApiService.textSearch(
                    query,
                    latLng.latitude,
                    latLng.longitude,
                    onSuccess = { response ->
                        _airportSearchResults.postValue(response.data)
                    },
                    onFailure = { error ->
                        // Handle error
                    })
            }
        }
    }

    private fun reverseGeocodeLocation(latLng: LatLng, callback: (String) -> Unit) {
        viewModelScope.launch {
            geolinkApiService.reverseGeocode(latLng.latitude, latLng.longitude).collect { result ->
                result.onSuccess { response ->
                    val address = "${response.data.address}, ${response.data.subAddress}"
                    callback(address)
                }.onFailure {
                    // Handle failure
                }
            }
        }
    }

    private fun isLocationArrived(currentLatLng: LatLng, targetLatLng: LatLng, threshold: Float = 50f): Boolean {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            currentLatLng.latitude, currentLatLng.longitude,
            targetLatLng.latitude, targetLatLng.longitude,
            results
        )
        return results[0] < threshold
    }

    fun createOrUpdateGeoQuery(id: String, center: GeoLocation, radius: Double) {
        sharedViewModel.createOrUpdateGeoQuery(id, center, radius)
    }

    fun addGeoQueryEventListener(id: String, listener: GeoQueryEventListener) {
        sharedViewModel.addGeoQueryEventListener(id, listener)
    }

    fun removeGeoQuery(id: String) {
        sharedViewModel.removeGeoQuery(id)
    }
}
