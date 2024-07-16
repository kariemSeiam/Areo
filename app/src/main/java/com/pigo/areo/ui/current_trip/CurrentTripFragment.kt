package com.pigo.areo.ui.current_trip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.GoogleMap
import com.pigo.areo.databinding.FragmentCurrentTripBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory

class CurrentTripFragment : Fragment() {

    private lateinit var binding: FragmentCurrentTripBinding

    private val sharedViewModel: SharedViewModel by activityViewModels {
        SharedViewModelFactory(requireContext().applicationContext)
    }

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
            routeResponse?.let {
                binding.tvIconLoc.text = routeResponse.distanceText
                binding.tvIconTime.text = routeResponse.durationText
            }
        }

        binding.fabCurrentLocation.setOnClickListener {
            if (::gMap.isInitialized) sharedViewModel.updateCameraPosition(sharedViewModel.waypoints.value)
        }


    }
}

