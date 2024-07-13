package com.pigo.areo.ui.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firebase.geofire.GeoLocation
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

    private val _driverLocation = MutableLiveData<String>()
    val driverLocation: LiveData<String> get() = _driverLocation

    private val _isTripRunning = MutableLiveData<Boolean>()
    val isTripRunning: LiveData<Boolean> get() = _isTripRunning

    private val _selectPilotLocationEvent = MutableLiveData<Unit>()
    val selectPilotLocationEvent: LiveData<Unit> get() = _selectPilotLocationEvent

    private val _selectDriverLocationEvent = MutableLiveData<Unit>()
    val selectDriverLocationEvent: LiveData<Unit> get() = _selectDriverLocationEvent

    private val _pilotSearchResults = MutableLiveData<List<SearchResult>>()
    val pilotSearchResults: LiveData<List<SearchResult>> get() = _pilotSearchResults

    private val _driverSearchResults = MutableLiveData<List<SearchResult>>()
    val driverSearchResults: LiveData<List<SearchResult>> get() = _driverSearchResults

    private val geolinkApiService = GeolinkApiService()

    private var pilotSearchJob: Job? = null
    private var driverSearchJob: Job? = null

    init {
        sharedViewModel.userRole.observeForever { userRole ->
            _isDriver.value = (userRole == SharedViewModel.UserRole.DRIVER)
            if (_isDriver.value == true) {
                sharedViewModel.driverLatLng?.let {
                    reverseGeocodeLocation(it) { address ->
                        _driverLocation.value = address
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


    }

    fun onRoleChanged(isDriver: Boolean) {
        _isDriver.value = isDriver
        sharedViewModel.switchUserRole()
    }

    fun updatePilotLocation(latLng: LatLng) {
        sharedViewModel.updatePilotLatLng(latLng)
    }

    fun updateDriverLocation(latLng: LatLng) {
        sharedViewModel.updateDriverLatLng(latLng)
    }

    fun toggleTripState() {
        _isTripRunning.value = !(_isTripRunning.value ?: false)
        if (_isTripRunning.value == true) {
            sharedViewModel.startLocationUpdates()
        } else {
            sharedViewModel.removeLocation("pilot_location")
            sharedViewModel.removeLocation("driver_location")
        }
    }

    fun selectPilotLocation() {
        _selectPilotLocationEvent.value = Unit
    }

    fun selectDriverLocation() {
        _selectDriverLocationEvent.value = Unit
    }

    fun confirmPilotLocation() {
        sharedViewModel.pilotLatLng?.let {
            sharedViewModel.updateLocation("pilot_location", GeoLocation(it.latitude, it.longitude))
        }
    }

    fun confirmDriverLocation() {
        sharedViewModel.driverLatLng?.let {
            sharedViewModel.updateLocation("driver_location", GeoLocation(it.latitude, it.longitude))
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

    fun performDriverLocationSearch(query: String) {
        driverSearchJob?.cancel()
        driverSearchJob = viewModelScope.launch {
            delay(2000) // 2 seconds delay
            sharedViewModel.currentLatLng.value?.let { latLng ->
                geolinkApiService.textSearch(
                    query,
                    latLng.latitude,
                    latLng.longitude,
                    onSuccess = { response ->
                        _driverSearchResults.postValue(response.data)
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
}
