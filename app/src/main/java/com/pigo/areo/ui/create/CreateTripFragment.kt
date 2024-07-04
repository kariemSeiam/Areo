package com.pigo.areo.ui.create

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.pigo.areo.R
import com.pigo.areo.databinding.FragmentCreateTripBinding

class CreateTripFragment : Fragment() {

    private val viewModel: CreateTripViewModel by viewModels()
    private lateinit var binding: FragmentCreateTripBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using DataBindingUtil
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_create_trip, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDataBinding()
        observeViewModel()
    }

    private fun setupDataBinding() {
        // Set the lifecycle owner to observe LiveData
        binding.lifecycleOwner = viewLifecycleOwner
        // Bind ViewModel
        binding.viewModel = viewModel
    }

    private fun observeViewModel() {
        // Example of observing LiveData from ViewModel
        viewModel.isDriver.observe(viewLifecycleOwner) { isDriver ->
            // Logic to show/hide airport location input based on user role
            binding.airportLocationInputLayout.visibility = if (isDriver) View.VISIBLE else View.GONE
        }
    }
}
