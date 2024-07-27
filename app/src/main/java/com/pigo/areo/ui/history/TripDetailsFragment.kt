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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentTripDetailsBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory

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

        sharedViewModel = ViewModelProvider(
            requireActivity(), SharedViewModelFactory(requireContext().applicationContext)
        )[SharedViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.tripDetailsRecyclerView)

        adapter = TripHistoryAdapter(mutableListOf()) { tripId ->
            sharedViewModel.deleteTrip(tripId) { success ->
                if (success) {
                    // Handle successful deletion
                }
            }
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Observe trip history LiveData
        sharedViewModel.tripHistory.observe(viewLifecycleOwner) { trips ->
            adapter.trips.clear()
            adapter.trips.addAll(trips)
            adapter.notifyDataSetChanged()
        }

        // Fetch trip history
        sharedViewModel.fetchTripHistory()

        // Set up swipe-to-delete with confirmation dialog
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

                // Show confirmation dialog
                MaterialAlertDialogBuilder(requireContext()).setTitle("Delete Trip")
                    .setMessage("Are you sure you want to delete this trip?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        adapter.notifyItemChanged(position)
                    }.setPositiveButton("Delete") { dialog, _ ->
                        sharedViewModel.deleteTrip(tripId) { success ->
                            if (success) {
                                adapter.trips.removeAt(position)
                                adapter.notifyItemRemoved(position)
                            } else {
                                adapter.notifyItemChanged(position)
                            }
                        }
                        dialog.dismiss()
                    }.setOnCancelListener {
                        adapter.notifyItemChanged(position)
                    }.show()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }
}
