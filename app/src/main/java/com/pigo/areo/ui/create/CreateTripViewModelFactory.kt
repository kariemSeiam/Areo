package com.pigo.areo.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pigo.areo.shared.SharedViewModel

class CreateTripViewModelFactory(
    private val sharedViewModel: SharedViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(CreateTripViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return CreateTripViewModel(sharedViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")


    }

}



