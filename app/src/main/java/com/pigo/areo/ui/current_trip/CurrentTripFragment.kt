package com.pigo.areo.ui.current_trip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pigo.areo.databinding.FragmentCurrentTripBinding

class CurrentTripFragment : Fragment() {

    private lateinit var binding: FragmentCurrentTripBinding
    private val currentTripViewModel: CurrentTripViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCurrentTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the GoogleMap instance from ViewModel
        currentTripViewModel.googleMap.observe(viewLifecycleOwner) { googleMap ->
            binding.fabCurrentLocation.setOnClickListener {
                currentTripViewModel.moveCameraToCurrentPosition()
            }
        }

 /*       // Start location updates
        viewModel.startLocationUpdates("locationKey")

        // Add location change listener
        viewModel.addLocationChangeListener("locationKey")

        // Query location by key
        viewModel.queryLocationByKey("locationKey") { latLng ->
            if (latLng != null) {
                // Do something with the LatLng
                println("Location: $latLng")
            } else {
                println("Location not found")
            }
        }*/

        // Example of accessing FloatingActionButton and setting onClickListener

    }
}
