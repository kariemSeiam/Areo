package com.pigo.areo.ui.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pigo.areo.R

class CreateTripViewModel : ViewModel() {
    private val _isDriver = MutableLiveData<Boolean>()
    val isDriver: LiveData<Boolean> = _isDriver

    private val _pilotLocation = MutableLiveData<String>()
    val pilotLocation: LiveData<String> = _pilotLocation

    private val _airportLocation = MutableLiveData<String>()
    val airportLocation: LiveData<String> = _airportLocation

    fun onRoleChanged(checkedId: Int) {
        _isDriver.value = checkedId == R.id.radio_driver
    }

    fun setPilotLocation(location: String) {
        _pilotLocation.value = location
    }

    fun setAirportLocation(location: String) {
        _airportLocation.value = location
    }
}
