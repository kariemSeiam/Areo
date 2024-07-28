package com.pigo.areo.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
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
import com.pigo.areo.geolink.models.findShortestPath
import com.pigo.areo.ui.current_trip.CustomLatLng
import com.pigo.areo.ui.current_trip.Trip
import com.pigo.areo.utils.CompassManager
import com.pigo.areo.utils.DataStoreUtil
import com.pigo.areo.utils.DataStoreUtil.dataStore
import com.pigo.areo.utils.PreferencesKeys
import com.pigo.areo.utils.SphericalUtil
import com.pigo.areo.utils.removeLocationTask
import com.pigo.areo.utils.setLocationTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SharedViewModel(context: Context) : ViewModel() {

    private val appContext: Context = context.applicationContext


    private val _userRole = MutableLiveData<UserRole>()
    val userRole: LiveData<UserRole> = _userRole

    private val _currentTrip = MutableLiveData<Trip?>()
    val currentTrip: LiveData<Trip?> = _currentTrip

    val _selectingState = MutableLiveData<Boolean>().apply { value = false }
    val selectingState: LiveData<Boolean> get() = _selectingState

    private val _googleMap = MutableLiveData<GoogleMap>()
    val googleMap: LiveData<GoogleMap> = _googleMap

    private val _currentLatLng = MutableLiveData<LatLng>()
    val currentLatLng: LiveData<LatLng> = _currentLatLng

    private val _cameraPosition = MutableLiveData<LatLng>()
    val cameraPosition: LiveData<LatLng> = _cameraPosition

    private val _routeResponse = MutableLiveData<Route?>()
    val routeResponse: LiveData<Route?> = _routeResponse

    private val _secRouteResponse = MutableLiveData<Route?>()
    val secRouteResponse: LiveData<Route?> = _secRouteResponse

    private val _lastTempLocation = MutableLiveData<CustomLatLng?>()
    val lastTempLocation: LiveData<CustomLatLng?> get() = _lastTempLocation

    private var initialCameraMove = true
    private lateinit var geoFire: GeoFire
    private val geoQueries = mutableMapOf<String, GeoQuery>()

    private var currentPolyline: Polyline? = null
    private var secondPolyline: Polyline? = null
    private var currentMarker: Marker? = null
    private var driverMarker: Marker? = null
    private var airportMarker: Marker? = null

    private val _tempRoute = MutableLiveData<Boolean?>()
    val tempRoute: LiveData<Boolean?> get() = _tempRoute

    private var autoMoveEnabled = true

    private val databaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("locations")
    private val tripDatabaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("trips")


    val tripHistory = MutableLiveData<List<Trip>>()



    private val defaultLatLng = LatLng(0.0, 0.0)
    private val apiService = GeolinkApiService()

    var pilotLatLng: LatLng? = null
    var driverLatLng: LatLng? = null
    var airportLatLng: LatLng? = null

    private val compassManager: CompassManager
    private val _bearing = MutableLiveData<Float>()
    val bearing: LiveData<Float> = _bearing

    init {
        initializeGeoFire()
        loadLastFetchedLatLng()
        loadLastSavedTrip()
        setupGeoQuery()
        fetchTripHistory()
        compassManager = CompassManager(appContext)
        startCompass()
    }

    override fun onCleared() {
        super.onCleared()
        stopCompass()
    }

    private fun startCompass() {
        viewModelScope.launch {
            compassManager.startCompassUpdates().collect { bearing ->
                _bearing.value = bearing
            }
        }
    }

    fun stopCompass() {
        compassManager.stop()
    }

    private fun setupCameraMoveListener() {
        googleMap.value?.setOnCameraMoveListener {
            _cameraPosition.value = googleMap.value?.cameraPosition?.target
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
        viewModelScope.launch {
            DataStoreUtil.getTripFlow(appContext).collect { trip ->
                trip?.let { _currentTrip.postValue(it) }
            }
        }
    }

    fun startTrip() {
        val tripId = UUID.randomUUID().toString()
        val trip = Trip(
            tripId, System.currentTimeMillis(), null, emptyList(), emptyList(), startTrip = true
        )
        _currentTrip.value = trip
        viewModelScope.launch {
            DataStoreUtil.clearTrip(appContext)
            DataStoreUtil.saveTrip(appContext, trip)
            saveTripToFirebase(trip)
        }
    }

    fun stopTrip() {
        _currentTrip.value?.let { trip ->
            val updatedTrip = trip.copy(endTime = System.currentTimeMillis())
            _currentTrip.value = updatedTrip
            saveTripToFirebase(updatedTrip)
            _currentTrip.value = null
            viewModelScope.launch {
                DataStoreUtil.clearTrip(appContext)

            }
        }
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


    fun addTripLocation(latLng: LatLng) {
        currentTrip.value?.let { trip ->
            val lastLocation = lastTempLocation.value

            if (lastLocation == null || distanceBetween(lastLocation.toLatLng(), latLng) > 3.0) {
                val updatedTrip = trip.copy(
                    coordinates = trip.coordinates + CustomLatLng.fromLatLng(latLng)
                )
                viewModelScope.launch {
                    DataStoreUtil.saveTrip(appContext, updatedTrip)

                }

                saveTripToFirebase(updatedTrip)
            }
        }
    }


    private fun distanceBetween(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude, end.latitude, end.longitude, results
        )
        return results[0].toDouble()
    }


    /*// Define a CoroutineExceptionHandler to handle exceptions
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("CoroutineError", "Exception caught: ${exception.localizedMessage}")
    }

    // Define your suspend function to generate trips
    suspend fun generateTripFromWaypoints() = supervisorScope {
        // Define start and end LatLngs for the request
        val startLatLng = LatLng(30.0444, 31.2357) // Example: Cairo
        val endLatLng = LatLng(33.5128, 36.2765) // Example: Damascus

        try {
            // Switch to IO Dispatcher for network operation
            val directionResponse = withContext(Dispatchers.IO) {
                apiService.direction(
                    startLatLng.latitude.toString(),
                    startLatLng.longitude.toString(),
                    endLatLng.latitude.toString(),
                    endLatLng.longitude.toString()
                ).firstOrNull()
            }

            // Process the response on the Main thread
            directionResponse?.onSuccess { result ->
                val bestRoute = findShortestPath(result.data)
                bestRoute?.let { route ->
                    val waypoints = route.waypoints.map { LatLng(it.lat, it.lng) }
                    val fakeTrip = Trip(
                        tripId = "trip1",
                        startTime = System.currentTimeMillis() - 3600000, // 1 hour ago
                        endTime = System.currentTimeMillis(),
                        coordinates = waypoints,
                        speeds = List(waypoints.size) { 50f + (it * 5) }, // Dummy speeds
                        startTrip = true,
                        arrivedAtPilot = false,
                        arrivedAtAirport = false
                    )

                    // Save to SharedPreferences or Firebase on IO Dispatcher
                    withContext(Dispatchers.IO) {
                        SharedPreferencesUtil.saveTrip(appContext, fakeTrip)
                        saveTripToFirebase(fakeTrip)
                    }
                }
            } ?: run {
                Log.e("GenerateTrip", "No direction response received")
            }
        } catch (e: Exception) {
            Log.e("GenerateTrip", "Error generating trip: ${e.localizedMessage}")
        }
    }
*/


    fun fetchTripHistory() {
        viewModelScope.launch {
            try {
                val tripsSnapshot = tripDatabaseReference.get().await()
                val trips = tripsSnapshot.children.mapNotNull { it.getValue(Trip::class.java) }
                tripHistory.postValue(trips)
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error fetching trip history", e)
                tripHistory.postValue(emptyList())
            }
        }
    }

    /*
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
        }*/

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
        airportLatLng = latLng
        updateLocation("airport_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "airport_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )
    }

    fun updatePilotLatLng(latLng: LatLng) {
        pilotLatLng = latLng
        updateLocation("pilot_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "pilot_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )
    }

    fun updateDriverLatLng(latLng: LatLng) {
        driverLatLng = latLng
        updateLocation("driver_location", GeoLocation(latLng.latitude, latLng.longitude))
        createOrUpdateGeoQuery(
            "driver_location", GeoLocation(latLng.latitude, latLng.longitude), 0.1
        )
    }

    private fun initializeGeoFire() {
        geoFire = GeoFire(databaseReference)
    }


    private val userRoleFlow: Flow<UserRole?> = appContext.dataStore.data.map { preferences ->
        val userRoleString = preferences[PreferencesKeys.USER_ROLE]
        userRoleString?.let { UserRole.valueOf(it) }
    }.catch {
        Log.e("SharedViewModel", "Error in userRoleFlow", it)
        emit(UserRole.PILOT) // Or another default UserRole value
    }


    // Method to save UserRole to DataStore
    private fun saveUserRole(userRole: UserRole) {
        viewModelScope.launch {
            appContext.dataStore.edit { preferences ->
                preferences[PreferencesKeys.USER_ROLE] = userRole.name
            }
        }
    }

    private fun loadUserRole() {
        viewModelScope.launch {
            userRoleFlow.collect { role ->
                if (role != null) {
                    _userRole.postValue(role)
                }
            }
        }
    }

    private fun updateDirectionResponse(routeResponse: Route, isAirportRoute: Boolean) {

        if (isAirportRoute) {
            _secRouteResponse.value = routeResponse
        } else {
            _routeResponse.value = routeResponse
        }
    }

    fun loginAs(role: UserRole) {
        _userRole.value = role
        saveUserRole(role)

    }


    fun setGoogleMap(map: GoogleMap) {
        _googleMap.value = map
        setupCameraMoveListener()
    }

    fun updateCurrentLatLng(latLng: LatLng) {
        _currentLatLng.postValue(latLng) // Updates the LiveData with the new LatLng
        saveLastFetchedLatLng(latLng) // Saves the last fetched LatLng (assuming it's a persistent storage operation)
        // Depending on the user's role, update the corresponding location

        loadUserRole()
        when (userRole.value) {
            UserRole.DRIVER -> updateDriverLatLng(latLng) // Updates driver's location
            UserRole.PILOT -> updatePilotLatLng(latLng) // Updates pilot's location
            else -> return
        }

        // If it's the initial camera move, update the camera position
        if (initialCameraMove) {
            updateCameraPosition() // Updates the camera position based on the new LatLng
        }
    }

    private fun calculateNearestWaypointIndex(currentLatLng: LatLng, waypoints: List<LatLng>): Int {
        return waypoints.indices.minByOrNull {
            SphericalUtil.computeDistanceBetween(currentLatLng, waypoints[it])
        } ?: 0
    }

    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val startLat = Math.toRadians(from.latitude)
        val startLng = Math.toRadians(from.longitude)
        val endLat = Math.toRadians(to.latitude)
        val endLng = Math.toRadians(to.longitude)
        val deltaLng = endLng - startLng
        val y = sin(deltaLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLng)

        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    fun updateCameraPosition(preZoom: Boolean = true) {
        val currentLatLng = _currentLatLng.value ?: return
        val userRole = _userRole.value
        val firstRoute = waypoints.value
        val secRoute = secWaypoints.value

        val combinedRoute = mutableListOf<LatLng>()
        firstRoute?.let { combinedRoute.addAll(it) }
        secRoute?.let { combinedRoute.addAll(it) }



        if (selectingState.value == true) {
            return
        }

        if (!autoMoveEnabled) {
            moveCameraToPosition(currentLatLng)
            return
        }

        when (userRole) {
            UserRole.DRIVER -> handleDriverCameraUpdate(currentLatLng, combinedRoute)
            UserRole.PILOT -> handlePilotCameraUpdate(preZoom)
            else -> {
                moveCameraToPosition(currentLatLng)

            }
        }
    }

    private fun handleDriverCameraUpdate(currentLatLng: LatLng, waypoints: List<LatLng>?) {
        if (waypoints.isNullOrEmpty() || waypoints.size < 2) {
            moveCameraToPosition(currentLatLng)
            return
        }

        val closestIndex = calculateNearestWaypointIndex(currentLatLng, waypoints)
        val nextIndex = (closestIndex + 1).coerceAtMost(waypoints.size - 1)
        val routeBearing = bearing.value ?: calculateBearing(currentLatLng, waypoints[nextIndex])

        val cameraPosition =
            CameraPosition.Builder().target(currentLatLng).zoom(19f).bearing(routeBearing).build()

        googleMap.value?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    initialCameraMove = false
                    updateCurrentMarkerAndAddMarkers(googleMap.value!!)
                }

                override fun onCancel() {
                    initialCameraMove = false
                    updateCurrentMarkerAndAddMarkers(googleMap.value!!)
                }
            })
    }

    private fun handlePilotCameraUpdate(preZoom: Boolean) {
        val currentLatLng = _currentLatLng.value ?: return
        val map = googleMap.value ?: return

        val boundsBuilder = LatLngBounds.Builder().include(currentLatLng)
        airportLatLng?.let { boundsBuilder.include(it) }
        driverLatLng?.let { boundsBuilder.include(it) }


        val bounds = boundsBuilder.build()

        map.setOnMapLoadedCallback {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        val newZoom =
                            if (preZoom) map.cameraPosition.zoom - 1f else map.cameraPosition.zoom - 0.7f
                        map.animateCamera(CameraUpdateFactory.zoomTo(newZoom))
                        initialCameraMove = false
                        updateCurrentMarkerAndAddMarkers(map)
                    }

                    override fun onCancel() {
                        initialCameraMove = false
                        updateCurrentMarkerAndAddMarkers(map)
                    }
                })
        }
    }

    fun setAutoMoveEnabled(enabled: Boolean) {
        autoMoveEnabled = enabled
    }

    fun moveCameraToPosition(latLng: LatLng) {
        googleMap.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
    }

    fun toggleAutoMoveEnabled(): Boolean {
        autoMoveEnabled = !autoMoveEnabled
        setAutoMoveEnabled(autoMoveEnabled)
        return autoMoveEnabled
    }


    fun updateCurrentMarkerAndAddMarkers(map: GoogleMap) {
        val markerSize = 100
        val icCar = getBitmapDescriptorFromVector(R.drawable.ic_car_custom, markerSize, markerSize)
        val icPilot =
            getBitmapDescriptorFromVector(R.drawable.ic_pilot_custom, markerSize, markerSize)
        val icPlane = getBitmapDescriptorFromVector(R.drawable.ic_airport, markerSize, markerSize)

        currentLatLng.value?.let { currentUserLatLng ->
            val icCurrent = when (_userRole.value) {
                UserRole.PILOT -> icPilot
                UserRole.DRIVER -> icCar
                else -> return
            }
            currentMarker?.remove()
            currentMarker = map.addMarker(
                MarkerOptions().position(currentUserLatLng).icon(icCurrent).anchor(0.5f, 0.5f)
            )
            when (_userRole.value) {
                UserRole.DRIVER -> driverLatLng = currentUserLatLng
                UserRole.PILOT -> pilotLatLng = currentUserLatLng
                else -> TODO()
            }
        }

        fetchAirportLocation { airportLocationLatLng ->
            airportLatLng = airportLocationLatLng
            airportLatLng?.let {
                airportMarker?.remove()
                airportMarker = map.addMarker(
                    MarkerOptions().position(it).icon(icPlane).anchor(0.5f, 0.5f)
                )
            }

            fetchOtherUserLocation { otherUserLatLng ->
                otherUserLatLng?.let {
                    // Calculate the distance between the two LatLng points
                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        currentLatLng.value!!.latitude,
                        currentLatLng.value!!.longitude,
                        it.latitude,
                        it.longitude,
                        distance
                    )
                    Log.e("TestSSSS", distance[0].toString())
                    val otherUserIconResId = when (_userRole.value) {
                        UserRole.PILOT -> icCar
                        UserRole.DRIVER -> icPilot
                        else -> TODO()
                    }

                    when (_userRole.value) {
                        UserRole.DRIVER -> {
                            pilotLatLng = otherUserLatLng
                        }

                        UserRole.PILOT -> {
                            driverLatLng = otherUserLatLng
                        }

                        else -> TODO()
                    }


                    // If the distance is greater than 10 meters, show the marker
                    if (distance[0] > 25) {
                        _tempRoute.postValue(false)
                        driverMarker?.remove()
                        driverMarker = map.addMarker(
                            MarkerOptions().position(it).icon(otherUserIconResId).anchor(0.5f, 0.5f)
                        )

                    } else {
                        _tempRoute.postValue(true)
                        // If the distance is 10 meters or less, remove the marker
                        driverMarker?.remove()
                    }
                    updateRoutesBasedOnRole(currentLatLng.value!!, it)

                }
            }
        }
    }

    private fun updateRoutesBasedOnRole(currentLatLng: LatLng, otherUserLatLng: LatLng) {
        val tempBool = (tempRoute.value == true)
        when (_userRole.value) {
            UserRole.DRIVER -> {
                if (airportLatLng != null) {
                    if (!tempBool) {
                        updateRoute(currentLatLng, otherUserLatLng, false) // Driver to Pilot
                    }
                    updateRoute(otherUserLatLng, airportLatLng!!, true) // Pilot to Airport
                } else {
                    updateRoute(currentLatLng, otherUserLatLng, false) // Driver to Pilot only
                }
            }

            UserRole.PILOT -> {
                if (airportLatLng != null) {
                    if (!tempBool) {
                        updateRoute(otherUserLatLng, currentLatLng, false) // Driver to Pilot
                    }
                    updateRoute(currentLatLng, airportLatLng!!, true) // Pilot to Airport
                } else {
                    updateRoute(otherUserLatLng, currentLatLng, false) // Driver to Pilot only

                }
            }

            else -> TODO()
        }
    }

    private fun updateRoute(startLatLng: LatLng, endLatLng: LatLng, isAirportRoute: Boolean) {
        val cachedRoute = getCachedRoute(startLatLng, endLatLng)
        if (cachedRoute != null) {
            // Use the cached route
            updateWaypoints(startLatLng, endLatLng, cachedRoute, isAirportRoute)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val directionResponse = apiService.direction(
                    startLatLng.latitude.toString(),
                    startLatLng.longitude.toString(),
                    endLatLng.latitude.toString(),
                    endLatLng.longitude.toString()
                ).firstOrNull()

                directionResponse?.onSuccess { result ->
                    val newWaypoints = parseRouteData(result, isAirportRoute)

                    // Cache the route
                    cacheRoute(startLatLng, endLatLng, newWaypoints)

                    // Update waypoints
                    updateWaypoints(startLatLng, endLatLng, newWaypoints, isAirportRoute)
                }?.onFailure { exception ->
                    Log.e("SharedViewModel", "Failed to get direction: ${exception.message}")
                }
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Exception during direction API call: ${e.message}")
            }
        }
    }

    private var cachedRoutes = mutableMapOf<Pair<LatLng, LatLng>, List<LatLng>>()

    private fun getCachedRoute(startLatLng: LatLng, endLatLng: LatLng): List<LatLng>? {
        return cachedRoutes.entries.find { (key, _) ->
            key.first.isWithinDistance(startLatLng) && key.second.isWithinDistance(endLatLng)
        }?.value
    }

    private fun cacheRoute(startLatLng: LatLng, endLatLng: LatLng, waypoints: List<LatLng>) {
        cachedRoutes[Pair(startLatLng, endLatLng)] = waypoints
    }

    private fun LatLng.isWithinDistance(other: LatLng, distance: Double = 2.0): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(
            this.latitude, this.longitude, other.latitude, other.longitude, results
        )
        return results[0] <= distance
    }

    private fun updateWaypoints(
        startLatLng: LatLng, endLatLng: LatLng, waypoints: List<LatLng>, isAirportRoute: Boolean
    ) {
        val currentWaypoints = mutableListOf(startLatLng).apply {
            addAll(waypoints)
            add(endLatLng)
        }

        if (isAirportRoute) {
            _secWaypoints.value = currentWaypoints
        } else {
            _waypoints.value = currentWaypoints
        }

        combineAndDrawRoutes()
    }

    private fun combineAndDrawRoutes() {
        val firstRoute = waypoints.value
        val secRoute = secWaypoints.value

        val combinedRoute = mutableListOf<LatLng>().apply {
            firstRoute?.let { addAll(it) }
            secRoute?.let { addAll(it) }
        }

        googleMap.value?.let {
            drawRouteOnMap(it, combinedRoute)
        }
    }

    private val _waypoints = MutableLiveData<List<LatLng>>()
    val waypoints: LiveData<List<LatLng>> = _waypoints

    private val _secWaypoints = MutableLiveData<List<LatLng>>()
    val secWaypoints: LiveData<List<LatLng>> = _secWaypoints

    private fun parseRouteData(
        directionResponse: DirectionResponse, isAirportRoute: Boolean
    ): List<LatLng> {
        return if (directionResponse.success) {
            findShortestPath(directionResponse.data)?.let { route ->
                updateDirectionResponse(route, isAirportRoute)
                route.waypoints.map { LatLng(it.lat, it.lng) }
            } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun drawRouteOnMap(
        googleMap: GoogleMap, waypoints: List<LatLng>, hexColor: String = "FF6750A4"
    ) {
        val color = Color.parseColor("#FF6750A4")
        val adjustedColor =
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                adjustColorForDarkMode(color)
            } else {
                Color.parseColor("#FF6750A4")
            }

        if (waypoints.isNotEmpty()) {
            val polylineOptions =
                PolylineOptions().width(14f).addAll(waypoints).color(adjustedColor)
            currentPolyline?.remove()
            currentPolyline = googleMap.addPolyline(polylineOptions)
        } else {
            Log.e("SharedViewModel", "No points to add to the polyline")
        }
    }

    private fun adjustColorForDarkMode(color: Int): Int {
        val factor = 0.5f
        return Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }


    fun setupGeoQuery() {
        listOf("pilot_location", "airport_location", "driver_location").forEach { id ->
            addGeoQueryEventListener(id, object : GeoQueryEventListener {
                override fun onKeyEntered(key: String?, location: GeoLocation?) {
                    Log.d(
                        "GeoQuery", "$id location entered the query: key=$key, location=$location"
                    )
                    when (id) {
                        "driver_location" -> {
                            if (key == "airport_location") {
                                stopTrip()
                            }
                            if (key == "pilot_location") hidePilotMarker(true)
                        }

                        else -> {}
                    }
                }

                override fun onKeyExited(key: String?) {
                    Log.d("GeoQuery", "$id location exited the query: key=$key")
                    when (id) {
                        "driver_location" -> {
                            if (key == "airport_location") stopTrip()
                            if (key == "pilot_location") hidePilotMarker(false)
                        }

                        else -> {}
                    }
                }

                override fun onKeyMoved(key: String?, location: GeoLocation?) {
                    Log.d(
                        "GeoQuery",
                        "$id location moved within the query: key=$key, location=$location"
                    )
                }

                override fun onGeoQueryReady() {
                    Log.d("GeoQuery", "$id location GeoQuery is ready")
                }

                override fun onGeoQueryError(error: DatabaseError?) {
                    Log.e(
                        "GeoQuery",
                        "Error with $id location GeoQuery: ${error?.message}",
                        error?.toException()
                    )
                }
            })
        }
    }

    private fun hidePilotMarker(hide: Boolean = false) {
        if (hide) currentMarker?.remove()

    }


    private fun getBitmapDescriptorFromVector(
        vectorResId: Int, width: Int, height: Int
    ): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(width + 40, height + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#266750A4") }
        canvas.drawCircle((width + 40) / 2f, (height + 40) / 2f, (width + height) / 4f, paint)

        ContextCompat.getDrawable(appContext, vectorResId)?.let { drawable ->
            drawable.setBounds(20, 20, width + 20, height + 20)
            drawable.draw(canvas)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun createOrUpdateGeoQuery(id: String, center: GeoLocation, radius: Double) {
        val geoQuery = geoFire.queryAtLocation(center, radius)
        geoQueries[id]?.removeAllListeners()
        geoQueries[id] = geoQuery
    }

    fun addGeoQueryEventListener(id: String, listener: GeoQueryEventListener) {
        geoQueries[id]?.addGeoQueryEventListener(listener)
    }

    fun removeGeoQuery(id: String) {
        geoQueries[id]?.removeAllListeners()
        geoQueries.remove(id)
    }

    fun updateLocation(key: String, location: GeoLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                geoFire.setLocationTask(key, location).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error updating location for key: $key", e)
            }
        }
    }

    fun removeLocation(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                geoFire.removeLocationTask(key).await()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error removing location for key: $key", e)
            }
        }
    }

    fun addLocationChangeListener(key: String) {
        databaseReference.child(key).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(GeoLocation::class.java)?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    if (_userRole.value == UserRole.PILOT) pilotLatLng = latLng
                    else if (_userRole.value == UserRole.DRIVER) driverLatLng = latLng
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SharedViewModel", "Data change cancelled for key: $key", error.toException())
            }
        })
    }

    fun queryLocationByKey(key: String, callback: (LatLng?) -> Unit) {
        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                callback(location?.let { LatLng(it.latitude, it.longitude) })
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                callback(null)
            }
        })
    }

    private fun fetchOtherUserLocation(callback: (LatLng?) -> Unit) {
        val key = if (_userRole.value == UserRole.PILOT) "driver_location" else "pilot_location"
        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                callback(location?.let { LatLng(it.latitude, it.longitude) })
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                callback(null)
            }
        })
    }

    private fun fetchAirportLocation(callback: (LatLng?) -> Unit) {
        geoFire.getLocation("airport_location", object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                callback(location?.let { LatLng(it.latitude, it.longitude) })
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                callback(null)
            }
        })
    }

    private fun saveLastFetchedLatLng(latLng: LatLng) {
        viewModelScope.launch {
            appContext.dataStore.edit { preferences ->
                preferences[PreferencesKeys.LAST_FETCHED_LAT] = latLng.latitude.toString()
                preferences[PreferencesKeys.LAST_FETCHED_LNG] = latLng.longitude.toString()
            }
        }
    }

    private fun loadLastFetchedLatLng() {
        viewModelScope.launch {
            DataStoreUtil.getLastFetchedLatLngFlow(appContext).collect { latLng ->
                _currentLatLng.postValue(latLng)
            }
        }
    }

    enum class UserRole {
        PILOT, DRIVER
    }


}

