package com.pigo.areo

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.pigo.areo.ui.current_trip.CurrentTripViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var currentTripViewModel: CurrentTripViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true && permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationUpdates()
            startPeriodicCameraUpdate()
        } else {
            // Handle permission denied scenario
        }
    }

    private var cameraUpdateJob: Job? = null // Coroutine job for periodic camera update

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        currentTripViewModel = viewModels<CurrentTripViewModel>().value

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationOnMap(location)
                }
            }
        }

        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        when {
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
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

    private fun startLocationUpdates() {
        // Check if permissions are granted
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions not granted, handle it as needed (e.g., request permissions)
            return
        }

        // Permissions granted, proceed to request location updates
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private var isFirstUpdate = true

    private fun updateLocationOnMap(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        currentTripViewModel.updateCurrentLatLng(currentLatLng)

        if (::gMap.isInitialized && isFirstUpdate) {
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
            gMap.animateCamera(cameraUpdate)
            isFirstUpdate = false
        }
    }


    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentContentMain.id) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.tripDetailsFragment,
                R.id.createTripFragment,
                R.id.currentTripFragment,
                R.id.settingsFragment
            )
        )
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun startPeriodicCameraUpdate() {
        // Cancel previous job if exists
        cameraUpdateJob?.cancel()

        // Start a new coroutine for periodic camera update
        cameraUpdateJob = lifecycleScope.launch {
            while (isActive) {
                delay(10000) // Delay for 10 seconds
                updateCameraPosition()
            }
        }
    }

    private fun updateCameraPosition() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions not granted, handle it as needed (e.g., request permissions)
            return
        }

        // Check if last known location is available
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                updateLocationOnMap(location)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        currentTripViewModel.setGoogleMap(googleMap)
        changeMapStyle()
        // Setup map here (e.g., set markers, move camera)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraUpdateJob?.cancel() // Cancel coroutine job when activity is destroyed
    }

    private fun changeMapStyle() {
        val style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
        gMap.setMapStyle(style)
    }
}
