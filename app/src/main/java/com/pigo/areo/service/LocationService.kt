package com.pigo.areo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.pigo.areo.R
import com.pigo.areo.shared.SharedViewModel.UserRole
import com.pigo.areo.data.model.CustomLatLng
import com.pigo.areo.data.model.Trip
import com.pigo.areo.utils.DataStoreUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_service_channel"
        private const val USER_ROLE_KEY = "user_role"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val databaseReference: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("locations")
    }
    private val tripDatabaseReference: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("trips")
    }
    private val geoFire: GeoFire by lazy {
        GeoFire(databaseReference)
    }
    private lateinit var sharedPreferences: SharedPreferences
    private var currentUserRole: UserRole? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTempLocation: CustomLatLng? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                Log.d("LocationService", "New Location: $latLng")
                serviceScope.launch {
                    updateLocationOnFirebase(latLng)
                    addTripLocation(latLng)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
            Log.d("LocationService", "Firebase initialized in service process")
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        currentUserRole = getUserRoleFromSharedPrefs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service started")
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        acquireWifiLock()
        startLocationUpdates()
        return START_STICKY
    }

    private fun updateLocationOnFirebase(latLng: LatLng) {
        val updatedUserRole = getUserRoleFromSharedPrefs()
        if (updatedUserRole != currentUserRole) {
            currentUserRole = updatedUserRole
            Log.d("LocationService", "User role updated to: $currentUserRole")
        }

        when (currentUserRole) {
            UserRole.DRIVER -> updateDriverLatLng(latLng)
            UserRole.PILOT -> updatePilotLatLng(latLng)
            else -> {

            }
        }
    }

    private fun updatePilotLatLng(latLng: LatLng) {
        try {
            geoFire.setLocation(
                "pilot_location", GeoLocation(latLng.latitude, latLng.longitude)
            )
            Log.d("LocationService", "Pilot location updated: $latLng")
        } catch (e: Exception) {
            Log.e("LocationService", "Error updating pilot location", e)
        }
    }

    private fun updateDriverLatLng(latLng: LatLng) {
        try {
            geoFire.setLocation(
                "driver_location", GeoLocation(latLng.latitude, latLng.longitude)
            )
            Log.d("LocationService", "Driver location updated: $latLng")
        } catch (e: Exception) {
            Log.e("LocationService", "Error updating driver location", e)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.create().apply {
                interval = 3000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            Log.e("LocationService", "Location permission not granted")
        }
    }

    private fun getUserRoleFromSharedPrefs(): UserRole? {
        val userRoleString = sharedPreferences.getString("user_role", null)
        return userRoleString?.let {
            UserRole.valueOf(it)
        }
    }


    private suspend fun addTripLocation(latLng: LatLng) {
        serviceScope.launch {
            val currentTrip = DataStoreUtil.getTripFlow(applicationContext).first()
            if (currentTrip != null) {
                if (lastTempLocation == null || distanceBetween(
                        lastTempLocation!!.toLatLng(), latLng
                    ) > 3.0
                ) {
                    val updatedTrip = currentTrip.copy(
                        coordinates = currentTrip.coordinates + CustomLatLng.fromLatLng(latLng)
                    )
                    DataStoreUtil.saveTrip(applicationContext, updatedTrip)
                    saveTripToFirebase(updatedTrip)
                    lastTempLocation = CustomLatLng.fromLatLng(latLng)
                }
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

    private fun saveTripToFirebase(trip: Trip) {
        serviceScope.launch {
            try {
                tripDatabaseReference.child(trip.tripId).setValue(trip).await()
                Log.d("LocationService", "Trip saved to Firebase: ${trip.tripId}")
            } catch (e: Exception) {
                Log.e("LocationService", "Error saving trip to Firebase", e)
            }
        }
    }


    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "LocationService::WakeLock"
        ).apply {
            acquire() // Acquire the wake lock immediately
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun acquireWifiLock() {
        wifiLock = (getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LocationService::WifiLock"
        ).apply {
            acquire() // Acquire the Wi-Fi lock immediately
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Location Service", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for location service"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBackgroundColor = ContextCompat.getColor(this, R.color.start_trip)

        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Location Service")
            .setContentText("Running...").setSmallIcon(R.drawable.ic_location_select)
            .setColor(notificationBackgroundColor).setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Location Service is running in the background...")
            ).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service destroyed")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        releaseWifiLock() // Release Wi-Fi lock
        stopForeground(true)
    }

}