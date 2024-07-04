package com.pigo.areo.ui.current_trip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.GoogleMap
import com.pigo.areo.databinding.FragmentCurrentTripBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory

class CurrentTripFragment : Fragment() {

    private lateinit var binding: FragmentCurrentTripBinding
    private val sharedViewModel: SharedViewModel by viewModels {
        SharedViewModelFactory(binding.root.context.applicationContext)
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
/*
            sharedViewModel.addMarkersToMap(googleMap)
*/


        }

        binding.fabCurrentLocation.setOnClickListener {
            sharedViewModel.loginAs(SharedViewModel.UserRole.PILOT)
            if (::gMap.isInitialized) sharedViewModel.moveCameraToCurrentPosition()
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
