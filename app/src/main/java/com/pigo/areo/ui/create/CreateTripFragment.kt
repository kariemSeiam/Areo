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
    private var debounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this

        sharedViewModel = ViewModelProvider(
            requireActivity(), SharedViewModelFactory(requireContext().applicationContext)
        )[SharedViewModel::class.java]

        createTripViewModel = ViewModelProvider(
            this,
            CreateTripViewModelFactory(sharedViewModel, binding.root.context.applicationContext)
        )[CreateTripViewModel::class.java]


        binding.viewModel = createTripViewModel

        setupClickListeners()
        setupRecyclerView()
        setupTextWatchers()
        setUpObservers() // Moved to after binding setup

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        showDriverLayout()
        airportLocation?.let {
            createTripViewModel.reverseGeocodeLocation(it) { res ->
                createTripViewModel._airportLocation.postValue(res.data.subAddress)
            }
        }

    }

    private fun setUpObservers() {
        sharedViewModel.userRole.observe(viewLifecycleOwner) { user ->
            showDriverLayout()
            binding.roleRadioGroup.check(
                when (user) {
                    SharedViewModel.UserRole.DRIVER -> R.id.radio_driver
                    SharedViewModel.UserRole.PILOT -> R.id.radio_pilot
                    else -> throw IllegalArgumentException("Unknown user role")
                }
            )
            // Enable or disable EditText and RecyclerView based on the user role and trip state
            updateEditTextState(user, createTripViewModel.isTripRunning.value ?: false)
        }

        createTripViewModel.isTripRunning.observe(viewLifecycleOwner) { isRunning ->
            binding.startTripButton.apply {
                setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(), if (isRunning) R.color.stop_trip else R.color.start_trip
                    )
                )
                text = getString(if (isRunning) R.string.stop_trip else R.string.start_trip)
            }
            // Enable or disable EditText and RecyclerView based on the user role and trip state
            val userRole = sharedViewModel.userRole.value ?: SharedViewModel.UserRole.DRIVER
            updateEditTextState(userRole, isRunning)
        }

        sharedViewModel.cameraPosition.observe(viewLifecycleOwner) { latLng ->
            airportLocation = latLng
            debounceJob?.cancel()
            debounceJob = lifecycleScope.launch {
                delay(50L)  // 0.05 second delay
                createTripViewModel.reverseGeocodeLocation(latLng) { response ->
                    binding.textCurrentAddress.text = response.data.address
                    binding.textCurrentSubAddress.text = response.data.subAddress
                }
            }
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
            showSelectLayout()
        }

        createTripViewModel.selectAirportLocationEvent.observe(viewLifecycleOwner) {
            showSelectLayout()
        }
    }


    private fun setEditTextEnabled(enabled: Boolean) {
        binding.airportLocationEditText.isEnabled = enabled
        // You can add more EditText fields here if needed
    }

    private fun updateEditTextState(userRole: SharedViewModel.UserRole, isTripRunning: Boolean) {
        val isEnabled = !isTripRunning && userRole == SharedViewModel.UserRole.DRIVER

        // Update EditText enabled state
        setEditTextEnabled(isEnabled)

        // Update RecyclerView visibility based on the conditions
        binding.airportLocationSearchResultsRecyclerView.visibility =
            if (isEnabled && createTripViewModel.airportSearchResults.value?.isNotEmpty() == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }


    private fun setupClickListeners() {

        binding.startTripButton.setOnClickListener { createTripViewModel.toggleTripState() }

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

    private fun setupRecyclerView() {
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
        sharedViewModel._selectingState.value = false
        binding.driverLayout.visibility = View.VISIBLE
        binding.selectingLayout.visibility = View.GONE
    }

    private fun showSelectLayout() {
        sharedViewModel._selectingState.value = true
        binding.driverLayout.visibility = View.GONE
        binding.selectingLayout.visibility = View.VISIBLE
    }
}
