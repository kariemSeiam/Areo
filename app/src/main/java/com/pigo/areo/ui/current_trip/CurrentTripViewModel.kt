package com.pigo.areo.ui.current_trip

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CurrentTripViewModel : ViewModel() {

    private val _googleMap = MutableLiveData<GoogleMap>()
    val googleMap: LiveData<GoogleMap> = _googleMap

    private val _currentLatLng = MutableLiveData<LatLng>()
    val currentLatLng: LiveData<LatLng> = _currentLatLng

    private var initialCameraMove = true
    private lateinit var geoFire: GeoFire
    private val geoQueries = mutableMapOf<String, GeoQuery>()

    private val polylineOptions = PolylineOptions().color(Color.BLUE).width(10f)

    private var currentMarker: Marker? = null

    private val databaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("locations")

    init {
        initializeGeoFire(databaseReference)
    }

    fun setGoogleMap(map: GoogleMap) {
        _googleMap.value = map
    }

    fun updateCurrentLatLng(latLng: LatLng) {
        _currentLatLng.value = latLng
        if (initialCameraMove) moveCameraToCurrentPosition()
        updateCurrentMarker(latLng)
    }

    fun moveCameraToCurrentPosition() {
        _currentLatLng.value?.let { latLng ->
            googleMap.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        initialCameraMove = false
                    }

                    override fun onCancel() {
                        initialCameraMove = false
                    }
                })
        }
    }

    private fun updateCurrentMarker(latLng: LatLng) {
        googleMap.value?.let { map ->
            currentMarker?.remove()
            currentMarker = map.addMarker(
                MarkerOptions().position(latLng).title("Current Position")
            )
        }
    }

    fun addRoutePoints(points: List<LatLng>) {
        polylineOptions.addAll(points)
        googleMap.value?.addPolyline(polylineOptions)
    }

    fun clearRoute() {
        polylineOptions.points.clear()
        googleMap.value?.clear()
    }

    fun addCustomMarker(latLng: LatLng, title: String, snippet: String, iconResId: Int) {
        googleMap.value?.addMarker(
            MarkerOptions().position(latLng).title(title).snippet(snippet)
                .icon(BitmapDescriptorFactory.fromResource(iconResId))
        )
    }

    fun changeMapStyle(styleResId: Int, context: Context) {
        viewModelScope.launch {
            try {
                val style = MapStyleOptions.loadRawResourceStyle(context, styleResId)
                googleMap.value?.setMapStyle(style)
            } catch (e: Resources.NotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun initializeGeoFire(reference: DatabaseReference) {
        geoFire = GeoFire(reference)
    }

    fun createOrUpdateGeoQuery(id: String, center: GeoLocation, radius: Double) {
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
                e.printStackTrace()
            }
        }
    }

    fun removeLocation(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                geoFire.removeLocationTask(key).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun startLocationUpdates(key: String) {
        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateCurrentLatLng(latLng)
                }
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                databaseError?.toException()?.printStackTrace()
            }
        })
    }

    fun addLocationChangeListener(key: String) {
        databaseReference.child(key).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(GeoLocation::class.java)
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateCurrentLatLng(latLng)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        })
    }

    fun queryLocationByKey(key: String, callback: (LatLng?) -> Unit) {
        geoFire.getLocation(key, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                callback(location?.let { LatLng(it.latitude, it.longitude) })
            }

            override fun onCancelled(databaseError: DatabaseError?) {
                databaseError?.toException()?.printStackTrace()
                callback(null)
            }
        })
    }

    private fun GeoFire.setLocationTask(key: String, location: GeoLocation): Task<Void> {
        val taskCompletionSource = TaskCompletionSource<Void>()
        this.setLocation(key, location) { _, error ->
            if (error != null) {
                taskCompletionSource.setException(error.toException())
            } else {
                taskCompletionSource.setResult(null)
            }
        }
        return taskCompletionSource.task
    }

    private fun GeoFire.removeLocationTask(key: String): Task<Void> {
        val taskCompletionSource = TaskCompletionSource<Void>()
        this.removeLocation(key) { _, error ->
            if (error != null) {
                taskCompletionSource.setException(error.toException())
            } else {
                taskCompletionSource.setResult(null)
            }
        }
        return taskCompletionSource.task
    }
}
