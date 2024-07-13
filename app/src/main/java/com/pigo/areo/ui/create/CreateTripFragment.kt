package com.pigo.areo.ui.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.databinding.FragmentCreateTripBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory

class CreateTripFragment : Fragment() {

    private lateinit var sharedViewModel: SharedViewModel
    private lateinit var createTripViewModel: CreateTripViewModel
    private lateinit var binding: FragmentCreateTripBinding
    private lateinit var pilotLocationAdapter: SearchResultAdapter
    private lateinit var driverLocationAdapter: SearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        // Initialize sharedViewModel with custom factory
        sharedViewModel = ViewModelProvider(
            requireActivity(), SharedViewModelFactory(requireContext().applicationContext)
        )[SharedViewModel::class.java]

        // Initialize createTripViewModel using sharedViewModel
        createTripViewModel = ViewModelProvider(
            this, CreateTripViewModelFactory(sharedViewModel)
        )[CreateTripViewModel::class.java]

        binding.viewModel = createTripViewModel

        setupRecyclerView()
        setupObservers()
        setupTextWatchers()

        // Observe the selected location from SelectLocationFragment
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<LatLng>("selectedLocation")
            ?.observe(viewLifecycleOwner) { latLng ->
                if (createTripViewModel.isDriver.value == true) {
                    createTripViewModel.updateDriverLocation(latLng)
                } else {
                    createTripViewModel.updatePilotLocation(latLng)
                }
            }

        return binding.root
    }

    private fun setupRecyclerView() {
        pilotLocationAdapter = SearchResultAdapter { searchResult ->
            val latLng = LatLng(searchResult.latitude, searchResult.longitude)
            createTripViewModel.updatePilotLocation(latLng)
        }

        binding.pilotLocationSearchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pilotLocationAdapter
        }

        driverLocationAdapter = SearchResultAdapter { searchResult ->
            val latLng = LatLng(searchResult.latitude, searchResult.longitude)
            createTripViewModel.updateDriverLocation(latLng)
        }

        binding.driverLocationSearchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = driverLocationAdapter
        }
    }

    private fun setupObservers() {
        createTripViewModel.isDriver.observe(viewLifecycleOwner) { isDriver ->
            // Handle role change if needed
        }

        createTripViewModel.selectPilotLocationEvent.observe(viewLifecycleOwner, Observer {
            // Navigate to SelectLocationFragment
            //findNavController().navigate(R.id.action_createTripFragment_to_selectLocationFragment)
        })

        createTripViewModel.selectDriverLocationEvent.observe(viewLifecycleOwner, Observer {
            // Navigate to SelectLocationFragment
            //findNavController().navigate(R.id.action_createTripFragment_to_selectLocationFragment)
        })

        createTripViewModel.pilotSearchResults.observe(viewLifecycleOwner, Observer { results ->
            pilotLocationAdapter.setItems(results)
        })

        createTripViewModel.driverSearchResults.observe(viewLifecycleOwner, Observer { results ->
            driverLocationAdapter.setItems(results)
        })

        createTripViewModel.pilotLocation.observe(viewLifecycleOwner, Observer { address ->
            binding.pilotLocationEditText.setText(address)
        })

        createTripViewModel.driverLocation.observe(viewLifecycleOwner, Observer { address ->
            binding.driverLocationEditText.setText(address)
        })
    }

    private fun setupTextWatchers() {
        binding.pilotLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    createTripViewModel.performPilotLocationSearch(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.driverLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    createTripViewModel.performDriverLocationSearch(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
