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
import com.pigo.areo.shared.SharedViewModel.UserRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateTripViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val geolinkApiService = GeolinkApiService()

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

    private var pilotSearchJob: Job? = null
    private var airportSearchJob: Job? = null

    init {
        observeUserRole()
        observeCurrentLocation()
    }

    private fun observeUserRole() {
        sharedViewModel.userRole.observeForever { userRole ->
            val isDriver = userRole == UserRole.DRIVER
            _isDriver.value = isDriver

            val location =
                if (isDriver) sharedViewModel.driverLatLng else sharedViewModel.pilotLatLng
            location?.let {
                reverseGeocodeLocation(it) { address ->
                    setInitialLocation(
                        address,
                        isDriver
                    )
                }
            }
        }
    }

    private fun setInitialLocation(address: String, isDriver: Boolean) {
        if (isDriver) {
            _airportLocation.value = address
        } else {
            _pilotLocation.value = address
        }
    }

    private fun observeCurrentLocation() {
        sharedViewModel.currentLatLng.observeForever { currentLatLng ->
            _isDriver.value?.let { isDriver ->
                val targetLatLng =
                    if (isDriver) sharedViewModel.pilotLatLng else sharedViewModel.driverLatLng
                targetLatLng?.let {
                    if (isLocationArrived(currentLatLng, it)) {
                        // Notify arrival at the location
                    }
                }
            }
        }
    }

    fun onRoleChanged(isDriver: Boolean) {
        _isDriver.value = isDriver
        val userRole = if (isDriver) UserRole.DRIVER else UserRole.PILOT
        sharedViewModel.loginAs(userRole)
    }


    fun updatePilotLocation(latLng: LatLng) {
        sharedViewModel.updatePilotLatLng(latLng)
    }

    fun updateAirportLocation(latLng: LatLng) {
        sharedViewModel.updateDriverLatLng(latLng)
    }

    fun toggleTripState() {
        val newState = !(_isTripRunning.value ?: false)
        _isTripRunning.value = newState
        if (newState) {
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
            sharedViewModel.updateLocation(
                "airport_location",
                GeoLocation(it.latitude, it.longitude)
            )
        }
    }

    fun performPilotLocationSearch(query: String) {
        pilotSearchJob?.cancel()
        pilotSearchJob = performLocationSearch(query, _pilotSearchResults)
    }

    fun performAirportLocationSearch(query: String) {
        airportSearchJob?.cancel()
        airportSearchJob = performLocationSearch(query, _airportSearchResults)
    }

    private fun performLocationSearch(
        query: String,
        resultsLiveData: MutableLiveData<List<SearchResult>>
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

    private fun reverseGeocodeLocation(latLng: LatLng, callback: (String) -> Unit) {
        viewModelScope.launch {
            geolinkApiService.reverseGeocode(latLng.latitude, latLng.longitude).collect { result ->
                result.onSuccess { response ->
                    val address = response.data.address
                    callback(address)
                }.onFailure { /* Handle failure */ }
            }
        }
    }

    private fun isLocationArrived(
        currentLatLng: LatLng,
        targetLatLng: LatLng,
        threshold: Float = 50f
    ): Boolean {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            currentLatLng.latitude,
            currentLatLng.longitude,
            targetLatLng.latitude,
            targetLatLng.longitude,
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
