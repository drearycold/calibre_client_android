package com.example.myapplication

import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class LibraryViewModel(private val libraryDAO: LibraryDAO) : ViewModel() {
    fun insertLibrary(vararg libraries: Library) {
        viewModelScope.launch {
            libraryDAO.insertAll(*libraries)
        }
    }

    fun updateLibrarySpinner(activity: MainActivity, selectedLibraryName: String? = null) {
        viewModelScope.launch {
            val libraryList = libraryDAO.getAll()
            val spinner = activity.findViewById<Spinner>(R.id.spLibraryListMain)
            ArrayAdapter(activity,
                android.R.layout.simple_spinner_item,
                libraryList
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
                selectedLibraryName?.let { libraryName ->
                    val selIndex = libraryList.indexOf(Library(libraryName))
                    if( selIndex >= 0)
                        spinner.setSelection(selIndex)
                }
            }
        }
    }

}