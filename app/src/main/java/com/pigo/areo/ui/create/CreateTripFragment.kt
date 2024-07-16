package com.pigo.areo.ui.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DatabaseError
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentCreateTripBinding
import com.pigo.areo.shared.SharedViewModel
import com.pigo.areo.shared.SharedViewModelFactory

class CreateTripFragment : Fragment() {

    private lateinit var sharedViewModel: SharedViewModel
    private lateinit var createTripViewModel: CreateTripViewModel
    private lateinit var binding: FragmentCreateTripBinding
    private lateinit var pilotLocationAdapter: SearchResultAdapter
    private lateinit var airportLocationAdapter: SearchResultAdapter

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

        setupClickListeners()

        // Observe the selected location from SelectLocationFragment
        sharedViewModel.userRole.observe(viewLifecycleOwner) { userRole ->
            Log.d("CreateTripFragment", "User role: $userRole")

            when (userRole) {
                SharedViewModel.UserRole.PILOT -> {
                    binding.roleRadioGroup.check(R.id.radio_pilot)
                    Log.d("CreateTripFragment", "Checked radio_pilot and updated airport location")
                }

                SharedViewModel.UserRole.DRIVER -> {
                    binding.roleRadioGroup.check(R.id.radio_driver)
                    Log.d("CreateTripFragment", "Checked radio_driver and updated pilot location")
                }
                null -> {
                    Log.d("CreateTripFragment", "User role is null")
                }
            }
        }

        /*sharedViewModel.currentTrip.observe(viewLifecycleOwner) { trip ->
            if (trip != null) {
                Log.d("CreateTripFragment", "Current trip: $trip")
                createTripViewModel.updateAirportLocation(latLng)

                // Handle the current trip, for example:
                // - Update UI with trip details
                // - Start navigation to the trip destination
                // - Etc.

            } else {
                createTripViewModel.updatePilotLocation(latLng)

                Log.d("CreateTripFragment", "Current trip is null")

                // Handle the null case, for example:
                // - Clear UI trip details
                // - Show a message that there is no active trip
                // - Etc.
            }
        }*/





        return binding.root
    }

    private fun setupClickListeners() {
        binding.btnRoleConfirm.setOnClickListener {
            setupObservers()
        }
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

        airportLocationAdapter = SearchResultAdapter { searchResult ->
            val latLng = LatLng(searchResult.latitude, searchResult.longitude)
            createTripViewModel.updateAirportLocation(latLng)
        }

        binding.airportLocationSearchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = airportLocationAdapter
        }
    }

    private fun setupObservers() {
        createTripViewModel.isDriver.observe(viewLifecycleOwner) { isDriver ->
            if (isDriver) {
                binding.driverLayout.visibility = View.VISIBLE
                binding.loginLayout.visibility = View.GONE
                setupRecyclerView()
                setupTextWatchers()
                createTripViewModel.selectPilotLocationEvent.observe(viewLifecycleOwner, Observer {
                    // Navigate to SelectLocationFragment
                    // findNavController().navigate(R.id.action_createTripFragment_to_selectLocationFragment)
                })

                createTripViewModel.selectAirportLocationEvent.observe(viewLifecycleOwner,
                    Observer {
                        // Navigate to SelectLocationFragment
                        // findNavController().navigate(R.id.action_createTripFragment_to_selectLocationFragment)
                    })

                createTripViewModel.pilotSearchResults.observe(viewLifecycleOwner,
                    Observer { results ->
                        pilotLocationAdapter.setItems(results)
                        binding.pilotLocationSearchResultsRecyclerView.visibility =
                            if (results.isEmpty()) View.GONE else View.VISIBLE
                    })

                createTripViewModel.airportSearchResults.observe(viewLifecycleOwner,
                    Observer { results ->
                        airportLocationAdapter.setItems(results)
                        binding.airportLocationSearchResultsRecyclerView.visibility =
                            if (results.isEmpty()) View.GONE else View.VISIBLE
                    })

                createTripViewModel.pilotLocation.observe(viewLifecycleOwner, Observer { address ->
                    binding.pilotLocationEditText.setText(address)
                })

                createTripViewModel.airportLocation.observe(viewLifecycleOwner,
                    Observer { address ->
                        binding.airportLocationEditText.setText(address)
                    })
            } else {
                binding.loginLayout.visibility = View.GONE
                binding.driverLayout.visibility = View.VISIBLE
                findNavController().navigate(R.id.action_createTripFragment_to_currentTripFragment)

            }
        }


    }

    private fun setupTextWatchers() {
        binding.pilotLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    pilotLocationAdapter.setItems(emptyList()) // Clear the adapter
                    createTripViewModel.performPilotLocationSearch(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.airportLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    airportLocationAdapter.setItems(emptyList()) // Clear the adapter
                    createTripViewModel.performAirportLocationSearch(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }


    private fun setupGeoQuery() {
        // Set up a GeoQuery for the pilot location with a radius of 100 meters
        val pilotLatLng = sharedViewModel.pilotLatLng
        if (pilotLatLng != null) {
            createTripViewModel.createOrUpdateGeoQuery(
                "pilot_location", GeoLocation(pilotLatLng.latitude, pilotLatLng.longitude), 0.1
            )

            createTripViewModel.addGeoQueryEventListener("pilot_location",
                object : GeoQueryEventListener {
                    override fun onKeyEntered(key: String?, location: GeoLocation?) {
                        // Handle when a location enters the query
                    }

                    override fun onKeyExited(key: String?) {
                        // Handle when a location exits the query
                    }

                    override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        // Handle when a location moves within the query
                    }

                    override fun onGeoQueryReady() {
                        // Handle when the query is ready
                    }

                    override fun onGeoQueryError(error: DatabaseError?) {
                        // Handle query errors
                    }
                })
        }

        // Set up a GeoQuery for the airport location with a radius of 500 meters
        val airportLatLng = sharedViewModel.driverLatLng
        if (airportLatLng != null) {
            createTripViewModel.createOrUpdateGeoQuery(
                "airport_location",
                GeoLocation(airportLatLng.latitude, airportLatLng.longitude),
                0.5
            )

            createTripViewModel.addGeoQueryEventListener("airport_location",
                object : GeoQueryEventListener {
                    override fun onKeyEntered(key: String?, location: GeoLocation?) {
                        // Handle when a location enters the query
                    }

                    override fun onKeyExited(key: String?) {
                        // Handle when a location exits the query
                    }

                    override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        // Handle when a location moves within the query
                    }

                    override fun onGeoQueryReady() {
                        // Handle when the query is ready
                    }

                    override fun onGeoQueryError(error: DatabaseError?) {
                        // Handle query errors
                    }
                })
        }
    }

    override fun onResume() {
        super.onResume()
        setupGeoQuery()
    }

    override fun onPause() {
        super.onPause()
        createTripViewModel.removeGeoQuery("pilot_location")
        createTripViewModel.removeGeoQuery("airport_location")
    }

}
