package com.example.myapplication

import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class LibraryViewModel(val libraryDAO: LibraryDAO) : ViewModel() {
    fun insertLibrary(vararg libraries: Library) {
        viewModelScope.launch {
            libraryDAO.insertAll(*libraries)
        }
    }

    fun updateLibrarySpinner(activity: MainActivity) {
        viewModelScope.launch {
            var libraryList = libraryDAO.getAll()
            var spinner = activity.findViewById<Spinner>(R.id.spLibraryListMain)
            ArrayAdapter<Library>(activity,
                android.R.layout.simple_spinner_item,
                libraryList
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
                spinner.setSelection(0)
            }
        }
    }

}