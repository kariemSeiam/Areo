package com.pigo.areo.shared

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.firebase.geofire.LocationCallback
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pigo.areo.R
import com.pigo.areo.geolink.GeolinkApiService
import com.pigo.areo.geolink.models.DirectionResponse
import com.pigo.areo.geolink.models.Route
import com.pigo.areo.geolink.models.RouteCache
import com.pigo.areo.geolink.models.findShortestPath
import com.pigo.areo.ui.current_trip.Trip
import com.pigo.areo.utils.CompassManager
import com.pigo.areo.utils.SharedPreferencesUtil
import com.pigo.areo.utils.SphericalUtil
import com.pigo.areo.utils.removeLocationTask
import com.pigo.areo.utils.setLocationTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SharedViewModel(context: Context) : ViewModel() {

    private var appContext: Context = context.applicationContext

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _userRole = MutableLiveData<UserRole>()
    val userRole: LiveData<UserRole> = _userRole

    private val _currentTrip = MutableLiveData<Trip?>()
    val currentTrip: LiveData<Trip?> = _currentTrip

    private val _googleMap = MutableLiveData<GoogleMap>()
    val googleMap: LiveData<GoogleMap> = _googleMap

    private val _currentLatLng = MutableLiveData<LatLng>()
    val currentLatLng: LiveData<LatLng> = _currentLatLng

    private val _cameraPosition = MutableLiveData<LatLng>()
    val cameraPosition: LiveData<LatLng> = _cameraPosition

    private val _routeResponse = MutableLiveData<Route?>()
    val routeResponse: LiveData<Route?> = _routeResponse

    private var initialCameraMove = true
    private lateinit var geoFire: GeoFire
    private val geoQueries = mutableMapOf<String, GeoQuery>()

    private var currentPolyline: Polyline? = null
    private var secondPolyline: Polyline? = null

    private var currentMarker: Marker? = null
    private var driverMarker: Marker? = null
    private var airportMarker: Marker? = null

    private var routeCache: RouteCache? = null
    private var autoMoveEnabled = true


    private val databaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("locations")

    private val tripDatabaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("trips")


    private val defaultLatLng = LatLng(0.0, 0.0) // Default LatLng if no data available

    private val apiService = GeolinkApiService()

    // Added local variables for pilot and driver LatLng
    var pilotLatLng: LatLng? = null
    var driverLatLng: LatLng? = null
    var airportLatLng: LatLng? = null

    // Add an instance of CompassManager
    private val compassManager: CompassManager

    private val _bearing = MutableLiveData<Float>()
    val bearing: LiveData<Float> = _bearing


    init {
        initializeGeoFire()
        loadUserRole()
        loadLastFetchedLatLng()
        loadLastSavedTrip()
        setupGeoQuery()
        compassManager = object : CompassManager(appContext) {}
        // Start receiving compass updates
        startCompass()
    }

    override fun onCleared() {
        super.onCleared()
        stopCompass()
    }

    // Start compass updates
    private fun startCompass() {
        viewModelScope.launch {
            compassManager.startCompassUpdates().collect { bearing ->
                _bearing.value = bearing
                // Handle the bearing update, e.g., update the UI or map orientation

            }
        }
    }

    // Stop compass updates
    fun stopCompass() {
        compassManager.stop()
    }

    private fun setupCameraMoveListener() {
        googleMap.value?.let { gMap ->
            gMap.setOnCameraMoveListener {
                val currentCameraPosition = gMap.cameraPosition.target
                _cameraPosition.value = currentCameraPosition
            }
        }
    }

    fun updateStartTripStatus(tripId: String, status: Boolean) {
        viewModelScope.launch {
            try {
                tripDatabaseReference.child(tripId).child("startTrip").setValue(status).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error updating startTrip status", e)
            }
        }
    }

    fun updateArrivedAtPilotStatus(tripId: String, status: Boolean) {
        viewModelScope.launch {
            try {
                tripDatabaseReference.child(tripId).child("arrivedAtPilot").setValue(status).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error updating arrivedAtPilot status", e)
            }
        }
    }

    fun updateArrivedAtAirportStatus(tripId: String, status: Boolean) {
        viewModelScope.launch {
            try {
                tripDatabaseReference.child(tripId).child("arrivedAtAirport").setValue(status)
                    .await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error updating arrivedAtAirport status", e)
            }
        }
    }


    private fun loadLastSavedTrip() {
        val trip = SharedPreferencesUtil.getTrip(appContext)
        _currentTrip.value = trip
    }

    fun startTrip() {
        val tripId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val trip = Trip(tripId, startTime, null, emptyList(), emptyList(), startTrip = true)
        _currentTrip.value = trip
        SharedPreferencesUtil.saveTrip(appContext, trip)
        saveTripToFirebase(trip)
    }

    fun stopTrip() {
        val trip = _currentTrip.value ?: return
        val endTime = System.currentTimeMillis()
        val updatedTrip = trip.copy(endTime = endTime)
        _currentTrip.value = updatedTrip
        saveTripToFirebase(updatedTrip)
        _currentTrip.value = null
        SharedPreferencesUtil.clearTrip(appContext)
    }

    private fun saveTripToFirebase(trip: Trip) {
        viewModelScope.launch {
            try {
                tripDatabaseReference.child(trip.tripId).setValue(trip).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error saving trip to Firebase", e)
            }
        }
    }

    fun addTripLocation(latLng: LatLng, speed: Float) {
        val trip = _currentTrip.value ?: return
        val updatedCoordinates = trip.coordinates.toMutableList().apply { add(latLng) }
        val updatedSpeeds = trip.speeds.toMutableList().apply { add(speed) }
        val updatedTrip = trip.copy(coordinates = updatedCoordinates, speeds = updatedSpeeds)
        _currentTrip.value = updatedTrip
        SharedPreferencesUtil.saveTrip(appContext, updatedTrip)
        saveTripToFirebase(updatedTrip)
    }

    fun getTripHistory(callback: (List<Trip>) -> Unit) {
        viewModelScope.launch {
            try {
                val tripsSnapshot = tripDatabaseReference.get().await()
                val trips = tripsSnapshot.children.mapNotNull { it.getValue(Trip::class.java) }
                callback(trips)
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error fetching trip history", e)
                callback(emptyList())
            }
        }
    }

    fun deleteTrip(tripId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                tripDatabaseReference.child(tripId).removeValue().await()
                callback(true)
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error deleting trip", e)
                callback(false)
            }
        }
    }


    fun updateAirportLocation(latLng: LatLng) {
        Log.d("SharedViewModel", "Updating Airport LatLng: $latLng")
        airportLatLng = latLng
        updateLocation("airport_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "airport_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )
    }

    fun updatePilotLatLng(latLng: LatLng) {
        Log.d("SharedViewModel", "Updating pilot LatLng: $latLng")
        pilotLatLng = latLng
        updateLocation("pilot_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "pilot_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )

    }

    fun updateDriverLatLng(latLng: LatLng) {
        Log.d("SharedViewModel", "Updating driver LatLng: $latLng")
        driverLatLng = latLng
        updateLocation("driver_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "driver_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )
    }

    private fun initializeGeoFire() {
        Log.d("SharedViewModel", "Initializing GeoFire")
        geoFire = GeoFire(databaseReference)
    }

    fun loadUserRole(): UserRole? {
        Log.d("SharedViewModel", "Loading user role from SharedPreferences")
        val userRoleString = sharedPreferences.getString("user_role", null)
        return if (userRoleString != null) {
            val role = UserRole.valueOf(userRoleString)
            _userRole.value = role
            Log.d("SharedViewModel", "Loaded user role: ${_userRole.value}")
            role
        } else {
            Log.d("SharedViewModel", "User role is null, performing custom action")
            // Open Login
            null
        }
    }

    private fun updateDirectionResponse(routeResponse: Route) {
        Log.d("SharedViewModel", "Updating route response: $routeResponse")
        _routeResponse.value = routeResponse
    }

    fun loginAs(role: UserRole) {
        Log.d("SharedViewModel", "Logging in as: $role")
        _userRole.value = role
        sharedPreferences.edit().putString("user_role", role.name).apply()
    }


    fun switchUserRole() {
        Log.d("SharedViewModel", "Switching user role")
        _userRole.value = when (_userRole.value) {
            UserRole.PILOT -> UserRole.DRIVER
            UserRole.DRIVER -> UserRole.PILOT
            else -> return
        }
        Log.d("SharedViewModel", "New user role: ${_userRole.value}")
        sharedPreferences.edit().putString("user_role", _userRole.value?.name).apply()
    }

    fun setGoogleMap(map: GoogleMap) {
        Log.d("SharedViewModel", "Setting GoogleMap instance")
        _googleMap.value = map
        setupCameraMoveListener()
    }

    fun updateCurrentLatLng(latLng: LatLng) {
        Log.d("SharedViewModel", "Updating current LatLng: $latLng")
        _currentLatLng.postValue(latLng)
        saveLastFetchedLatLng(latLng)
        if (userRole.value != null) {
            when (userRole.value) {
                UserRole.DRIVER -> {
                    updateDriverLatLng(latLng)
                }

                UserRole.PILOT -> {
                    updatePilotLatLng(latLng)
                }

                null -> TODO()
            }
        }
        if (initialCameraMove) updateCameraPosition()
    }


    // Function to calculate the nearest waypoint index
    private fun calculateNearestWaypointIndex(currentLatLng: LatLng, waypoints: List<LatLng>): Int {
        var nearestIndex = 0
        var minDistance = SphericalUtil.computeDistanceBetween(currentLatLng, waypoints[0])

        for (i in 1 until waypoints.size) {
            val distance = SphericalUtil.computeDistanceBetween(currentLatLng, waypoints[i])
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = i
            }
        }

        return nearestIndex
    }

    // Function to calculate bearing between two LatLng points
    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val startLat = Math.toRadians(from.latitude)
        val startLng = Math.toRadians(from.longitude)
        val endLat = Math.toRadians(to.latitude)
        val endLng = Math.toRadians(to.longitude)

        val deltaLng = endLng - startLng

        val y = sin(deltaLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLng)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        bearing = (bearing + 360) % 360 // Ensure the result is between 0 and 360
        return bearing
    }

    // Function to update camera position based on user role and route direction
    fun updateCameraPosition(
        waypoints: List<LatLng>? = null, preZoom: Boolean = true
    ) {
        val currentLatLng = _currentLatLng.value
        val userRole = _userRole.value
        val map = googleMap.value

        if (map == null) {
            Log.e("SharedViewModel", "GoogleMap instance is null")
            return
        }

        if (currentLatLng == null) {
            Log.e("SharedViewModel", "Current LatLng is null")
            return
        }

        if (!autoMoveEnabled) {
            Log.d("SharedViewModel", "Auto-move is disabled")
            return
        }

        when (userRole) {
            UserRole.DRIVER -> {
                if (waypoints.isNullOrEmpty()) {
                    moveCameraToPosition(currentLatLng)
                    Log.e("SharedViewModel", "Waypoints list is empty")
                    return
                }

                if (waypoints.size < 2) return

                val closestIndex = calculateNearestWaypointIndex(currentLatLng, waypoints)
                val nextIndex = (closestIndex + 1).coerceAtMost(waypoints.size - 1)
                val routeBearingMatrix = calculateBearing(currentLatLng, waypoints[nextIndex])
                val routeBearing = bearing.value

                val cameraPosition = if (routeBearing != null) {
                    CameraPosition.Builder().target(currentLatLng).zoom(19f).bearing(routeBearing)
                        .build()
                    //.tilt(60f) // Adjust tilt as needed for a street-level view

                } else {
                    CameraPosition.Builder().target(currentLatLng).zoom(19f)
                        .bearing(routeBearingMatrix).build()
                    //.tilt(60f) // Adjust tilt as needed for a street-level view

                }

                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition),
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            initialCameraMove = false
                            updateCurrentMarkerAndAddMarkers(map)
                        }

                        override fun onCancel() {
                            initialCameraMove = false
                            updateCurrentMarkerAndAddMarkers(map)
                        }
                    })
            }

            UserRole.PILOT -> {
                if (driverLatLng != null) {
                    val bounds =
                        LatLngBounds.Builder().include(currentLatLng).include(driverLatLng!!)
                            .build()
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 8)
                    map.animateCamera(cameraUpdate, object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            val currentZoom = map.cameraPosition.zoom
                            val newZoom = if (preZoom) currentZoom - 1f else currentZoom - 0.7f
                            val zoomUpdate = CameraUpdateFactory.zoomTo(newZoom)
                            map.animateCamera(zoomUpdate)
                            initialCameraMove = false
                            updateCurrentMarkerAndAddMarkers(map)
                        }

                        override fun onCancel() {
                            initialCameraMove = false
                            updateCurrentMarkerAndAddMarkers(map)
                        }
                    })
                } else {
                    Log.e("SharedViewModel", "Driver location is null")
                }
            }

            else -> {
                Log.e("SharedViewModel", "Unknown user role")
            }
        }
    }

    fun setAutoMoveEnabled(enabled: Boolean) {
        autoMoveEnabled = enabled
    }

    fun moveCameraToPosition(latLng: LatLng) {
        googleMap.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        toggleAutoMoveEnabled()
    }

    private fun toggleAutoMoveEnabled() {
        autoMoveEnabled = !autoMoveEnabled
        setAutoMoveEnabled(autoMoveEnabled)
    }


    // Function to check if a new request is needed based on time and distance
    private fun isNewRequestNeeded(currentLatLng: LatLng): Boolean {
        val now = System.currentTimeMillis()
        routeCache?.let {
            val timeElapsed = now - it.lastRequestTime
            val distance = SphericalUtil.computeDistanceBetween(currentLatLng, it.lastLatLng)

            Log.d("SharedViewModel", "Time elapsed since last request: $timeElapsed ms")
            Log.d("SharedViewModel", "Distance from last request location: $distance meters")

            // Check if more than 10 seconds have passed or distance > 100 meters
            if (timeElapsed < 10000 || distance < 100) {
                Log.d(
                    "SharedViewModel",
                    "New request not needed: Time elapsed < 60000 ms and distance < 100 meters"
                )
                return false
            }
        }

        // Update the cache with the current time and location
        routeCache = routeCache?.directionResponse?.let { RouteCache(now, currentLatLng, it) }
        Log.d("SharedViewModel", "New request needed: Either no route cache or conditions met")
        return true
    }


    // Function to update the cache with new data
    private fun updateRouteCache(currentLatLng: LatLng, directionResponse: DirectionResponse) {
        val now = System.currentTimeMillis()
        routeCache = RouteCache(now, currentLatLng, directionResponse)
    }

    fun updateCurrentMarkerAndAddMarkers(map: GoogleMap) {
        val markerSize = 80 // Desired size of the marker icon

        // Get bitmap descriptors for different roles and locations
        val icCar = getBitmapDescriptorFromVector(R.drawable.ic_car_custom, markerSize, markerSize)
        val icPilot =
            getBitmapDescriptorFromVector(R.drawable.ic_pilot_custom, markerSize, markerSize)
        val icPlane = getBitmapDescriptorFromVector(R.drawable.ic_airport, markerSize, markerSize)

        // Update current user's marker
        currentLatLng.value?.let { currentUserLatLng ->
            val icCurrent = when (_userRole.value) {
                UserRole.PILOT -> icPilot
                UserRole.DRIVER -> icCar
                else -> return@let
            }
            currentMarker?.remove()
            currentMarker = map.addMarker(
                MarkerOptions().position(currentUserLatLng).icon(icCurrent)
                    .anchor(0.5f, 0.5f) // Center the marker icon
            )
        }

        // Fetch and update airport location marker
        fetchAirportLocation { airportLocationLatLng ->
            airportLocationLatLng?.let {
                airportLatLng = it
                airportMarker?.remove()
                airportMarker = map.addMarker(
                    MarkerOptions().position(it).icon(icPlane)
                        .anchor(0.5f, 0.5f) // Center the marker icon
                )
                // Update routes based on the user role
                if (_userRole.value == UserRole.PILOT) {
                    updateRoute(map, _currentLatLng.value!!, it, true) // Pilot to Airport
                } else if (_userRole.value == UserRole.DRIVER) {
                    pilotLatLng?.let { pilotLocation ->
                        updateRoute(map, pilotLocation, it, false) // Driver to Pilot
                    }

                }
            }
        }

        // Fetch and update other user's location marker
        fetchOtherUserLocation { otherUserLatLng ->
            otherUserLatLng?.let {
                val otherUserIconResId = when (_userRole.value) {
                    UserRole.PILOT -> icCar
                    UserRole.DRIVER -> icPilot
                    else -> return@fetchOtherUserLocation
                }
                driverMarker?.remove()
                driverMarker = map.addMarker(
                    MarkerOptions().position(it).icon(otherUserIconResId)
                        .anchor(0.5f, 0.5f) // Center the marker icon
                )
                // Update routes based on the user role
                if (_userRole.value == UserRole.DRIVER) {
                    updateRoute(map, _currentLatLng.value!!, it, false) // Driver to Other User
                } else if (_userRole.value == UserRole.PILOT) {
                    updateRoute(map, it, _currentLatLng.value!!, false) // Pilot to Other User
                }
            }
        }
    }

    // Function to update the route based on the locations
    private fun updateRoute(
        map: GoogleMap, startLatLng: LatLng, endLatLng: LatLng, isAirportRoute: Boolean
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val directionFlow = apiService.direction(
                    startLatLng.latitude.toString(),
                    startLatLng.longitude.toString(),
                    endLatLng.latitude.toString(),
                    endLatLng.longitude.toString()
                )

                val directionResult = directionFlow.firstOrNull()
                directionResult?.let { result ->
                    result.onSuccess { directionResponse ->
                        fetchAndDrawRoute(directionResponse, isAirportRoute)
                    }.onFailure { exception ->
                        Log.e("SharedViewModel", "Failed to get direction: ${exception.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Exception during direction API call: ${e.message}")
            }
        }
    }


    fun setupGeoQuery() {
        addGeoQueryEventListener("pilot_location", object : GeoQueryEventListener {
            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery", "Pilot location entered the query: key=$key, location=$location"
                )
            }

            override fun onKeyExited(key: String?) {
                Log.d("GeoQuery", "Pilot location exited the query: key=$key")
            }

            override fun onKeyMoved(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery",
                    "Pilot location moved within the query: key=$key, location=$location"
                )
            }

            override fun onGeoQueryReady() {
                Log.d("GeoQuery", "Pilot location GeoQuery is ready")
            }

            override fun onGeoQueryError(error: DatabaseError?) {
                Log.e(
                    "GeoQuery",
                    "Error with Pilot location GeoQuery: ${error?.message}",
                    error?.toException()
                )
            }
        })

        addGeoQueryEventListener("airport_location", object : GeoQueryEventListener {
            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery", "Airport location entered the query: key=$key, location=$location"
                )
            }

            override fun onKeyExited(key: String?) {
                Log.d("GeoQuery", "Airport location exited the query: key=$key")
            }

            override fun onKeyMoved(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery",
                    "Airport location moved within the query: key=$key, location=$location"
                )
            }

            override fun onGeoQueryReady() {
                Log.d("GeoQuery", "Airport location GeoQuery is ready")
            }

            override fun onGeoQueryError(error: DatabaseError?) {
                Log.e(
                    "GeoQuery",
                    "Error with Airport location GeoQuery: ${error?.message}",
                    error?.toException()
                )
            }
        })

        addGeoQueryEventListener("driver_location", object : GeoQueryEventListener {
            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery", "Driver location entered the query: key=$key, location=$location"
                )
            }

            override fun onKeyExited(key: String?) {
                Log.d("GeoQuery", "Driver location exited the query: key=$key")
            }

            override fun onKeyMoved(key: String?, location: GeoLocation?) {
                Log.d(
                    "GeoQuery",
                    "Driver location moved within the query: key=$key, location=$location"
                )
            }

            override fun onGeoQueryReady() {
                Log.d("GeoQuery", "Driver location GeoQuery is ready")
            }

            override fun onGeoQueryError(error: DatabaseError?) {
                Log.e(
                    "GeoQuery",
                    "Error with Driver location GeoQuery: ${error?.message}",
                    error?.toException()
                )
            }
        })
    }


    private val _waypoints = MutableLiveData<List<LatLng>>()
    val waypoints: LiveData<List<LatLng>> = _waypoints


    private fun parseRouteData(directionResponse: DirectionResponse): List<LatLng> {
        val waypoints = mutableListOf<LatLng>()

        if (directionResponse.success) {
            findShortestPath(directionResponse.data)?.let { route ->
                updateDirectionResponse(route)
                route.waypoints.forEach { waypoint ->
                    waypoints.add(LatLng(waypoint.lat, waypoint.lng))
                }
            }
        }
        _waypoints.value = waypoints
        return waypoints
    }

    private fun fetchAndDrawRoute(
        directionResponse: DirectionResponse, isSecondRoute: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val waypoints = parseRouteData(directionResponse)
                googleMap.value?.let { map ->
                    drawRouteOnMap(map, waypoints, isSecondRoute)
                } ?: run {
                    Log.e("SharedViewModel", "GoogleMap instance is null, cannot draw route")
                }
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error fetching or drawing route", e)
            }
        }
    }

    private fun drawRouteOnMap(
        googleMap: GoogleMap,
        waypoints: List<LatLng>,
        isSecondRoute: Boolean = false,
        hexColor: String = "FF6750A4"
    ) {
        val color = Color.parseColor("#$hexColor")
        val adjustedColor =
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                adjustColorForDarkMode(color)
            } else {
                color
            }

        if (waypoints.isNotEmpty()) {
            if (isSecondRoute) {
                // Clear the previous polyline if it exists
                secondPolyline?.remove()
                val polylineOptions =
                    PolylineOptions().width(14f).addAll(waypoints).color(adjustedColor)
                secondPolyline = googleMap.addPolyline(polylineOptions)
                Log.d("SharedViewModel", "Polyline added to the map with color: $adjustedColor")
            } else {
                // Clear the previous polyline if it exists
                currentPolyline?.remove()

                val polylineOptions =
                    PolylineOptions().width(14f).addAll(waypoints).color(adjustedColor)
                currentPolyline = googleMap.addPolyline(polylineOptions)
                Log.d("SharedViewModel", "Polyline added to the map with color: $adjustedColor")
            }

        } else {
            Log.e("SharedViewModel", "No points to add to the polyline")
        }
    }

    private fun adjustColorForDarkMode(color: Int): Int {
        // Example adjustment: Darken the color by reducing brightness
        val factor = 0.5f
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }

    private fun getBitmapDescriptorFromVector(
        vectorResId: Int, width: Int, height: Int
    ): BitmapDescriptor {
        val backgroundCircleColor = Color.parseColor("#266750A4") // 10% transparency
        val circleSize = ((width + height) / 2) + 40 // Adjust the circle size for padding

        // Create a square bitmap to accommodate the circle and icon
        val bitmap = Bitmap.createBitmap(circleSize, circleSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw the background circle
        paint.color = backgroundCircleColor
        val circleRadius = (circleSize / 2).toFloat()
        canvas.drawCircle(circleRadius, circleRadius, circleRadius, paint)

        // Draw the icon in the center of the circle
        val vectorDrawable: Drawable? = ContextCompat.getDrawable(appContext, vectorResId)
        vectorDrawable?.let { drawable ->
            val inset = (circleSize - width) / 2
            drawable.setBounds(inset, inset, inset + width, inset + height)
            drawable.draw(canvas)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun createOrUpdateGeoQuery(id: String, center: GeoLocation, radius: Double) {
        Log.d(
            "SharedViewModel",
            "Creating or updating GeoQuery with ID: $id, center: $center, radius: $radius"
        )
        val geoQuery = geoFire.queryAtLocation(center, radius)
        geoQueries[id]?.removeAllListeners()
        geoQueries[id] = geoQuery

    }


    fun addGeoQueryEventListener(id: String, listener: GeoQueryEventListener) {
        Log.d("SharedViewModel", "Adding GeoQueryEventListener for ID: $id")
        geoQueries[id]?.addGeoQueryEventListener(listener)
    }

    fun removeGeoQuery(id: String) {
        Log.d("SharedViewModel", "Removing GeoQuery for ID: $id")
        geoQueries[id]?.removeAllListeners()
        geoQueries.remove(id)
    }

    fun updateLocation(key: String, location: GeoLocation) {
        Log.d("SharedViewModel", "Updating location for key: $key, location: $location")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                geoFire.setLocationTask(key, location).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error updating location for key: $key", e)
            }
        }
    }

    fun removeLocation(key: String) {
        Log.d("SharedViewModel", "Removing location for key: $key")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                geoFire.removeLocationTask(key).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error removing location for key: $key", e)
            }
        }
    }

    fun addLocationChangeListener(key: String) {
        Log.d("SharedViewModel", "Adding location change listener for key: $key")
        databaseReference.child(key).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(GeoLocation::class.java)?.let { location ->
                    Log.d("SharedViewModel", "Data change for key: $key, location: $location")
                    val latLng = LatLng(location.latitude, location.longitude)
                    if (_userRole.value == UserRole.PILOT) {
                        pilotLatLng = latLng
                    } else if (_userRole.value == UserRole.DRIVER) {
                        driverLatLng = latLng
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    "SharedViewModel", "Data change cancelled for key: $key", error.toException()
                )
            }
        })
    }

    fun queryLocationByKey(key: String, callback: (LatLng?) -> Unit) {
        Log.d("SharedViewModel", "Querying location by key: $key")
        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                Log.d("SharedViewModel", "Location result for key: $key, location: $location")
                val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                if (_userRole.value == UserRole.PILOT) {
                    driverLatLng = latLng
                } else if (_userRole.value == UserRole.DRIVER) {
                    pilotLatLng = latLng
                }
                callback(latLng)
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                Log.e(
                    "SharedViewModel", "Query cancelled for key: $key", databaseError?.toException()
                )
                callback(null)
            }
        })
    }

    private fun fetchOtherUserLocation(callback: (LatLng?) -> Unit) {
        val key = when (_userRole.value) {
            UserRole.PILOT -> "driver_location"
            UserRole.DRIVER -> "pilot_location"
            else -> return
        }
        Log.d("SharedViewModel", "Fetching other user location for key: $key")

        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                Log.d(
                    "SharedViewModel",
                    "Other user location result for key: $key, location: $location"
                )
                val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                if (_userRole.value == UserRole.PILOT) {
                    driverLatLng = latLng
                } else if (_userRole.value == UserRole.DRIVER) {
                    pilotLatLng = latLng
                }
                callback(latLng)
                if (location == null) {
                    Log.e("SharedViewModel", "Other user location result is null for key: $key")
                }
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                Log.e(
                    "SharedViewModel",
                    "Error fetching other user location for key: $key",
                    databaseError?.toException()
                )
                callback(null)
            }
        })
    }

    private fun fetchAirportLocation(callback: (LatLng?) -> Unit) {
        val key = "airport_location"
        Log.d("SharedViewModel", "Fetching airport location for key: $key")

        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                Log.d(
                    "SharedViewModel", "Airport location result for key: $key, location: $location"
                )
                val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                callback(latLng)
                if (location == null) {
                    Log.e("SharedViewModel", "Airport location result is null for key: $key")
                }
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                Log.e(
                    "SharedViewModel",
                    "Error fetching airport location for key: $key",
                    databaseError?.toException()
                )
                callback(null)
            }
        })
    }


    private fun saveLastFetchedLatLng(latLng: LatLng) {
        Log.d("SharedViewModel", "Saving last fetched LatLng: $latLng")
        sharedPreferences.edit().putString("last_fetched_lat", latLng.latitude.toString()).apply()
        sharedPreferences.edit().putString("last_fetched_lng", latLng.longitude.toString()).apply()
    }

    private fun loadLastFetchedLatLng() {
        Log.d("SharedViewModel", "Loading last fetched LatLng from SharedPreferences")
        val lat = sharedPreferences.getString("last_fetched_lat", null)?.toDoubleOrNull()
            ?: defaultLatLng.latitude
        val lng = sharedPreferences.getString("last_fetched_lng", null)?.toDoubleOrNull()
            ?: defaultLatLng.longitude
        _currentLatLng.value = LatLng(lat, lng)
        Log.d("SharedViewModel", "Loaded LatLng: ${_currentLatLng.value}")
    }

    enum class UserRole {
        PILOT, DRIVER
    }
}


