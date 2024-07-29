package com.pigo.areo

import android.Manifest
import android.annotation.SuppressLint
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
        private const val RETRY_DELAY_MS = 10000L // 10 seconds
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val sharedViewModel: SharedViewModel by viewModels {
        SharedViewModelFactory(applicationContext)
    }

    private var cameraUpdateJob: Job? = null
    private var permissionRetryJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value }
        if (allPermissionsGranted) {
            checkBatteryOptimization()
        } else {
            Log.e("PermissionError", "Required permissions denied.")
            startPermissionRetryCoroutine()
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
                R.id.tripDetailsFragment,
                R.id.createTripFragment,
                R.id.currentTripFragment
            )
        )
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun requestLocationPermissions() {
        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WAKE_LOCK
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                plus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        val permissionsToRequest = locationPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            checkBatteryOptimization()
        }
    }

    private fun startPermissionRetryCoroutine() {
        permissionRetryJob = lifecycleScope.launch {
            while (isActive) {
                if (arePermissionsGranted()) {
                    permissionRetryJob?.cancel()
                    checkBatteryOptimization()
                    return@launch
                }
                Log.d("PermissionRetry", "Retrying permission request...")
                delay(RETRY_DELAY_MS)
                requestLocationPermissions()
            }
        }
    }

    private fun arePermissionsGranted(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WAKE_LOCK
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                plus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            requestDisableBatteryOptimization()
        } else {
            checkGpsAndStartService()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestDisableBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun checkGpsAndStartService() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startLocationService()
        } else {
            openLocationSettings()
        }
    }

    private fun startLocationService() {
        fetchLastKnownLocation()
        startPeriodicCameraUpdate()
    }

    private fun fetchLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
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
                    sharedViewModel.addTripLocation(LatLng(location.latitude, location.longitude))
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
                sharedViewModel.updateCameraPosition()
                delay(if (sharedViewModel.userRole.value == SharedViewModel.UserRole.DRIVER) 1500 else 5000)
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
        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        Log.d("LocationUpdate", "Animated camera to new location: $latLng")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        val uiSettings = gMap.uiSettings.apply {
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
            isIndoorLevelPickerEnabled = false
        }

        changeMapStyle()
        sharedViewModel.setGoogleMap(gMap)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraUpdateJob?.cancel()
        permissionRetryJob?.cancel()
    }

    private fun changeMapStyle() {
        val style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
        gMap.setMapStyle(style)
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }
}
