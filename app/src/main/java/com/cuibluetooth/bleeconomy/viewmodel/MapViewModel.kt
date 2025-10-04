package com.cuibluetooth.bleeconomy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.cuibluetooth.bleeconomy.model.Person
import com.cuibluetooth.bleeconomy.model.PersonId
import com.cuibluetooth.bleeconomy.repository.PositionRepository

class MapViewModel : ViewModel() {
    private val repository = PositionRepository()
    val persons : LiveData<Map<PersonId, Person>> = repository.getPersons().asLiveData()

    // TODO Call this function when a batch arrives
    fun updatePositions ( newBatch : List<Person>){
        repository.updateFromBatch(newBatch)
    }
}