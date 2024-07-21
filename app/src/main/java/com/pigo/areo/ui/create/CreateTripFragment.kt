package com.pigo.areo.ui.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.model.LatLng
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentCreateTripBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateTripFragment : Fragment() {

    private lateinit var sharedViewModel: SharedViewModel
    private lateinit var createTripViewModel: CreateTripViewModel
    private lateinit var binding: FragmentCreateTripBinding
    private lateinit var airportLocationAdapter: SearchResultAdapter

    private var airportLocation: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = ViewModelProvider(
            requireActivity(), SharedViewModelFactory(requireContext().applicationContext)
        )[SharedViewModel::class.java]

        createTripViewModel = ViewModelProvider(
            this, CreateTripViewModelFactory(sharedViewModel)
        )[CreateTripViewModel::class.java]

        binding.viewModel = createTripViewModel


        setupClickListeners()
        setupRecyclerViews()
        setupTextWatchers()

        return binding.root
    }


    private var debounceJob: Job? = null


    private fun setUpObservers() {
        sharedViewModel.userRole.observe(viewLifecycleOwner) { user ->
            showDriverLayout()
            when (user) {
                SharedViewModel.UserRole.DRIVER -> {
                    binding.roleRadioGroup.check(R.id.radio_driver)
                }

                SharedViewModel.UserRole.PILOT -> {
                    binding.roleRadioGroup.check(R.id.radio_pilot)
                }
                else -> TODO()
            }

        }

        createTripViewModel.isTripRunning.observe(viewLifecycleOwner) { isRunning ->
            if (isRunning) {
                binding.startTripButton.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(), R.color.stop_trip
                    )
                )
                binding.startTripButton.text = getString(R.string.stop_trip)
            } else {
                binding.startTripButton.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(), R.color.start_trip
                    )
                )
                binding.startTripButton.text = getString(R.string.start_trip)
            }
        }

        sharedViewModel.cameraPosition.observe(viewLifecycleOwner) { latLng ->
            airportLocation = latLng
            debounceJob?.cancel()
            debounceJob = lifecycleScope.launch {
                delay(50L)  // 0.05 second delay
                createTripViewModel.reverseGeocodeLocation(latLng) { response ->
                    // Handle the reverse geocode response
                    binding.textCurrentAddress.text = response.data.address
                    binding.textCurrentSubAddress.text = response.data.subAddress
                }
            }
        }

        sharedViewModel.cameraPosition.observe(viewLifecycleOwner) {
            airportLocation = it

        }

        createTripViewModel.pilotLocation.observe(viewLifecycleOwner) {
            binding.pilotLocationEditText.setText(it)
        }

        createTripViewModel.airportLocation.observe(viewLifecycleOwner) {
            binding.airportLocationEditText.setText(it)
        }


        createTripViewModel.airportSearchResults.observe(viewLifecycleOwner) { results ->
            airportLocationAdapter.setItems(results)
            binding.airportLocationSearchResultsRecyclerView.visibility =
                if (results.isEmpty()) View.GONE else View.VISIBLE
        }

        createTripViewModel.selectPilotLocationEvent.observe(viewLifecycleOwner) {
            // Navigate to SelectLocationFragment
            showSelectLayout()
        }

        createTripViewModel.selectAirportLocationEvent.observe(viewLifecycleOwner) {
            // Navigate to SelectLocationFragment
            showSelectLayout()
        }


    }


    private fun setupClickListeners() {
        binding.btnRoleConfirm.setOnClickListener {
            setUpObservers()
        }

        binding.startTripButton.setOnClickListener {
            createTripViewModel.toggleTripState()
        }

        binding.buttonConfirmAddress.setOnClickListener {
            airportLocation?.let {
                createTripViewModel.reverseGeocodeLocation(it) { res ->
                    createTripViewModel._airportLocation.postValue(res.data.address)
                    sharedViewModel.updateAirportLocation(it)
                    showDriverLayout()
                }
            }

        }

    }

    private fun setupRecyclerViews() {

        airportLocationAdapter = SearchResultAdapter { searchResult ->
            val latLng = LatLng(searchResult.latitude, searchResult.longitude)
            sharedViewModel.updateAirportLocation(latLng)
        }

        binding.airportLocationSearchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = airportLocationAdapter
        }
    }

    private fun setupTextWatchers() {
        binding.airportLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                createTripViewModel.performAirportLocationSearch(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }


    private fun showDriverLayout() {
        binding.driverLayout.visibility = View.VISIBLE
        binding.loginLayout.visibility = View.GONE
        binding.selectingLayout.visibility = View.GONE
    }

    private fun showSelectLayout() {
        binding.driverLayout.visibility = View.GONE
        binding.loginLayout.visibility = View.GONE
        binding.selectingLayout.visibility = View.VISIBLE
    }


}
