package com.pigo.areo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.pigo.areo.service.LocationService
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val sharedViewModel: SharedViewModel by viewModels {
        SharedViewModelFactory(applicationContext)
    }

    private var cameraUpdateJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true && permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true && permissions[Manifest.permission.WAKE_LOCK] == true) {
            checkBatteryOptimization()
        } else {
            Log.e("PermissionError", "Location or wake lock permissions denied.")
            // Handle permission denial (e.g., show a message to the user)
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

    private fun requestLocationPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        // Request background location permission on Android 10 (API level 29) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (permissionsToRequest.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            // All permissions already granted
            checkBatteryOptimization()
        } else {
            // Request permissions
            requestPermissionLauncher.launch(
                permissionsToRequest.toTypedArray()
            )
        }
    }
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            requestDisableBatteryOptimization()
        } else {
            // Battery optimization is disabled, now check for GPS
            checkGpsAndStartService()
        }
    }

    private fun checkGpsAndStartService() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // GPS is enabled, start location service
            startLocationService()
        } else {
            // GPS is not enabled, prompt user to enable it
            openLocationSettings()
        }
    }


    private fun startLocationService() {

        // Start location updates after starting the service
        fetchLastKnownLocation()
        startPeriodicCameraUpdate()
    }

    @SuppressLint("BatteryLife")
    private fun requestDisableBatteryOptimization() {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun fetchLastKnownLocation() {
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
                        LatLng(location.latitude, location.longitude)
                    )

                }
            }
        }, null)

        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }


    private fun startPeriodicCameraUpdate() {
        cameraUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val userRole = sharedViewModel.userRole.value
                sharedViewModel.updateCameraPosition()
                delay(if (userRole == SharedViewModel.UserRole.DRIVER) 1500 else 5000)
            }
        }
    }

    private fun updateLocationOnMap(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        sharedViewModel.updateCurrentLatLng(currentLatLng)
        Log.d("LocationUpdate", "Updated current LatLng: $currentLatLng")

        if (::gMap.isInitialized) {
            sharedViewModel.updateCurrentMarkerAndAddMarkers(gMap)
            Log.d("LocationUpdate", "Updated current marker and added markers")
        }

    }

    private fun animateCameraToLocation(latLng: LatLng) {
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
        gMap.animateCamera(cameraUpdate)
        Log.d("LocationUpdate", "Animated camera to new location: $latLng")
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