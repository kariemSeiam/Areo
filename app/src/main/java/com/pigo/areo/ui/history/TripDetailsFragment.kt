package com.pigo.areo.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentTripDetailsBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory
import com.pigo.areo.ui.current_trip.Trip

class TripDetailsFragment : Fragment() {

    private lateinit var binding: FragmentTripDetailsBinding
    private lateinit var sharedViewModel: SharedViewModel
    private lateinit var adapter: TripHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentTripDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize sharedViewModel with custom factory
        sharedViewModel = ViewModelProvider(
            requireActivity(), SharedViewModelFactory(requireContext().applicationContext)
        )[SharedViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.tripDetailsRecyclerView)

        // Create fake trips data
        val fakeTrips = listOf(
            Trip(
                tripId = "1",
                startTime = System.currentTimeMillis() - 3600000,
                endTime = System.currentTimeMillis(),
                coordinates = listOf(
                    LatLng(37.7749, -122.4194),
                    LatLng(37.7849, -122.4094),
                    LatLng(37.7949, -122.3994)
                ),
                speeds = listOf(10f, 20f, 30f)
            ), Trip(
                tripId = "2",
                startTime = System.currentTimeMillis() - 7200000,
                endTime = System.currentTimeMillis() - 3600000,
                coordinates = listOf(
                    LatLng(34.0522, -118.2437),
                    LatLng(34.0622, -118.2337),
                    LatLng(34.0722, -118.2237)
                ),
                speeds = listOf(15f, 35f, 45f)
            )
        )

        adapter = TripHistoryAdapter(fakeTrips.toMutableList()) { tripId ->
            sharedViewModel.deleteTrip(tripId) { success ->
                // Handle delete result
            }
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set up swipe-to-delete
        val itemTouchHelperCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val tripId = adapter.trips[position].tripId
                sharedViewModel.deleteTrip(tripId) { success ->
                    if (success) {
                        adapter.trips.removeAt(position)
                        adapter.notifyItemRemoved(position)
                    } else {
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }
}
