package com.cuibluetooth.bleeconomy.repository
import com.cuibluetooth.bleeconomy.model.Coordinates
import com.cuibluetooth.bleeconomy.model.Person
import com.cuibluetooth.bleeconomy.model.PersonId
import com.cuibluetooth.bleeconomy.model.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


class PositionRepository {

    private var personsMap = mutableMapOf<PersonId,Person>()
    private val _personsFlow = MutableStateFlow<Map<PersonId, Person>>(emptyMap())

    init{
        // Simulate batch
        simulateBatch()
    }

    fun getPersons(): Flow<Map<PersonId, Person>> = _personsFlow

    // Later: Calling when Batch arrives
    fun updateFromBatch(newBatch : List<Person>){
        newBatch.forEach{ newPerson->
            val existing = personsMap[newPerson.id]
            if(existing != null){
                //Interpolation: Hold last if there's no new (check ts)
                if(newPerson.actual.ts != null){
                    existing.actual = newPerson.actual
                } // Else {Keeping existing.actual}
            } else{
                personsMap[newPerson.id] = newPerson
            }
        }
        _personsFlow.value = personsMap.toMap()
    }

    private fun simulateBatch(){
        //Dummy data
        val batch = listOf(
            Student(1,"Student1", Coordinates(2.0, 3.0), Coordinates(-1.0, -2.0), projectName = "App movil",
                institutionalCode = "20191508"),
            Student(2, "Student2", Coordinates(-4.0, 5.0), null, projectName = "Vehiculo movil",
                institutionalCode = "20150632")
        )
        updateFromBatch(batch)
    }
}