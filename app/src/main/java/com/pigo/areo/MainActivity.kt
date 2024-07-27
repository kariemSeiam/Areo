package com.pigo.areo

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.pigo.areo.databinding.ActivityMainBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val sharedViewModel: SharedViewModel by viewModels {
        SharedViewModelFactory(applicationContext)
    }

    private var cameraUpdateJob: Job? = null // Coroutine job for periodic camera update

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true && permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLastKnownLocation()
            startLocationUpdates()
            startPeriodicCameraUpdate()
        } else {
            // Handle permission denied scenario
            Log.e("PermissionError", "Location permissions were denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        when {
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLastKnownLocation()
                startLocationUpdates()
                startPeriodicCameraUpdate()
            }

            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun fetchLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    updateLocationOnMap(location)
                } else {
                    Log.w("LocationWarning", "Last known location is null.")
                    if (ActivityCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Open location settings if permission is granted and location data is null
                        openLocationSettings()
                    }
                }
            }.addOnFailureListener { exception ->
                Log.e("LocationError", "Failed to get last known location: ${exception.message}")
            }
        } else {
            Log.e("PermissionError", "Location permissions are not granted.")
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 4000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationOnMap(location)
                    sharedViewModel.addTripLocation(
                        LatLng(location.latitude, location.longitude), location.speed
                    )
                }
            }
        }, null)
    }

    private var isFirstUpdate = true

    private fun updateLocationOnMap(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        sharedViewModel.updateCurrentLatLng(currentLatLng)
        Log.d("LocationUpdate", "Updated current LatLng: $currentLatLng")

        if (::gMap.isInitialized) {
            sharedViewModel.updateCurrentMarkerAndAddMarkers(gMap)
            Log.d("LocationUpdate", "Updated current marker and added markers")
        }

        if (::gMap.isInitialized && isFirstUpdate) {
            animateCameraToLocation(currentLatLng)
            isFirstUpdate = false
        }
    }

    private fun animateCameraToLocation(latLng: LatLng) {
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
        gMap.animateCamera(cameraUpdate)
        Log.d("LocationUpdate", "Animated camera to new location: $latLng")
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentContentMain.id) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.tripDetailsFragment, R.id.createTripFragment, R.id.currentTripFragment
            )
        )
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun startPeriodicCameraUpdate() {
        cameraUpdateJob?.cancel()
        cameraUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val userRole = sharedViewModel.userRole.value
                sharedViewModel.updateCameraPosition()
                delay(if (userRole == SharedViewModel.UserRole.DRIVER) 1500 else 5000)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        sharedViewModel.setGoogleMap(googleMap)
        gMap = googleMap
        val uiSettings = gMap.uiSettings

        uiSettings.isCompassEnabled = false
        uiSettings.isMyLocationButtonEnabled = false
        uiSettings.isMapToolbarEnabled = false
        uiSettings.isIndoorLevelPickerEnabled = false

        changeMapStyle()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraUpdateJob?.cancel() // Cancel coroutine job when activity is destroyed
    }

    private fun changeMapStyle() {
        val style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
        gMap.setMapStyle(style)
    }

    private fun openLocationSettings() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.android.settings", "com.android.settings.Settings\$LocationSettingsActivity"
            )
        }
        startActivity(intent)
    }
}
