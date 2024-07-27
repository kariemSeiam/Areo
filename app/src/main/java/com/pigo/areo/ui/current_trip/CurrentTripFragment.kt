package com.pigo.areo.ui.current_trip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.GoogleMap
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentCurrentTripBinding
import com.pigo.areo.geolink.models.Route
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory
import com.pigo.areo.ui.create.CreateTripViewModel

class CurrentTripFragment : Fragment() {

    private lateinit var binding: FragmentCurrentTripBinding


    private val sharedViewModel: SharedViewModel by activityViewModels {
        SharedViewModelFactory(requireContext().applicationContext)
    }
    private val currentTipViewModel: CreateTripViewModel by viewModels()

    private lateinit var gMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCurrentTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the GoogleMap instance from ViewModel
        sharedViewModel.googleMap.observe(viewLifecycleOwner) { googleMap ->
            gMap = googleMap
            // sharedViewModel.addMarkersToMap(googleMap)
        }


        //viewModel.currentTrip.observe(this) { trip ->
        //            // Update UI with current trip data
        //        }
        //        viewModel.deleteTrip(tripId) { success ->
        //                if (success) {
        //                    // Trip deleted successfully
        //                } else {
        //                    // Failed to delete trip
        //                }
        //            }
        //        viewModel.getTripHistory { trips ->
        //            // Update UI with trip history
        //        }

        sharedViewModel.routeResponse.observe(viewLifecycleOwner) { routeResponse ->
            sharedViewModel.secRouteResponse.value?.let { secRouteResponse ->
                updateUI(routeResponse, secRouteResponse)
            } ?: updateUI(routeResponse, null)
        }

        sharedViewModel.secRouteResponse.observe(viewLifecycleOwner) { secRouteResponse ->
            sharedViewModel.routeResponse.value?.let { routeResponse ->
                updateUI(routeResponse, secRouteResponse)
            } ?: updateUI(null, secRouteResponse)
        }

        binding.fabCurrentLocation.setOnClickListener {
            val iconRes = if (!sharedViewModel.toggleAutoMoveEnabled()) {
                R.drawable.ic_gps
            } else {
                R.drawable.ic_compass1
            }
            sharedViewModel._selectingState.value = (iconRes == R.drawable.ic_gps)


            binding.fabCurrentLocation.setImageResource(iconRes)
            sharedViewModel.updateCameraPosition()
        }


    }


    override fun onResume() {
        super.onResume()
        sharedViewModel.setupGeoQuery()
    }

    override fun onPause() {
        super.onPause()
        sharedViewModel.removeGeoQuery("pilot_location")
        sharedViewModel.removeGeoQuery("airport_location")
        sharedViewModel.removeGeoQuery("driver_location")
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "$hours h ${minutes % 60} min"
            minutes > 0 -> "$minutes min"
            else -> "$seconds sec"
        }
    }

    private fun formatDistance(meters: Int): String {
        val kilometers = meters / 1000.0
        return when {
            kilometers <= 0.5 -> {
                // Show as "0.5 km" for values <= 500 meters
                "0.5 km"
            }

            kilometers <= 1.0 -> {
                // Show as "1.0 km" for values > 500 meters and <= 1000 meters
                "1.0 km"
            }

            else -> {
                // For distances greater than 1000 meters, show rounded km value
                val roundedKilometers = Math.round(kilometers).toInt()
                "$roundedKilometers km"
            }
        }
    }


    private fun updateUI(routeResponse: Route?, secRouteResponse: Route?) {
        val totalDistanceMeters =
            (routeResponse?.distanceMeters ?: 0) + (secRouteResponse?.distanceMeters ?: 0)
        val totalDurationSeconds =
            (routeResponse?.durationSeconds ?: 0) + (secRouteResponse?.durationSeconds ?: 0)

        binding.tvIconLoc.text = formatDistance(totalDistanceMeters)
        binding.tvIconTime.text = formatDuration(totalDurationSeconds)
    }

}

