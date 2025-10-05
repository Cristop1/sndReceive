package com.cuibluetooth.bleeconomy

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.cuibluetooth.bleeconomy.R
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cuibluetooth.bleeconomy.model.Person
import com.cuibluetooth.bleeconomy.model.PersonId
import com.cuibluetooth.bleeconomy.model.Student
import com.cuibluetooth.bleeconomy.ui.MapView
import com.cuibluetooth.bleeconomy.ui.StudentAdapter
import com.cuibluetooth.bleeconomy.viewmodel.MapViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
    private lateinit var panelCard: MaterialCardView
    private lateinit var panelToggle: ImageButton
    private lateinit var panelTitle: TextView
    private lateinit var filtersToggle: ImageButton
    private lateinit var panelContent: View
    private lateinit var filtersContainer: View
    private lateinit var panelToggleHint: TextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var filterNameInput: TextInputEditText
    private lateinit var filterCodeInput: TextInputEditText
    private lateinit var filterProjectInput: TextInputEditText
    private lateinit var studentAdapter: StudentAdapter

    private var isPanelExpanded = false
    private var areFiltersExpanded = false
    private var allStudents: List<Student> = emptyList()
    private val trackedStudentsIds : MutableSet<PersonId> = mutableSetOf()
    private var personsMap: Map<PersonId, Person> = emptyMap()
    private var isAdvertising = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map_view)
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]

        panelCard = findViewById(R.id.panel_container)
        panelToggle = findViewById(R.id.btn_panel_fold)
        panelTitle = findViewById(R.id.panel_title)
        filtersToggle = findViewById(R.id.btn_filters_toggle)
        panelContent = findViewById(R.id.panel_content_container)
        filtersContainer = findViewById(R.id.filter_fields_container)
        panelToggleHint = findViewById(R.id.panel_toggle_hint)

        searchInput = findViewById(R.id.search_edit_text)
        filterNameInput = findViewById(R.id.filter_name_edit_text)
        filterCodeInput = findViewById(R.id.filter_code_edit_text)
        filterProjectInput = findViewById(R.id.filter_project_edit_text)

        studentAdapter = StudentAdapter { student, isTracked ->
            if (isTracked) {
                trackedStudentsIds.add(student.id)
            } else {
                trackedStudentsIds.remove(student.id)
            }
            applyFilters()
            refreshMapPersons()
        }
        val studentRecycler = findViewById<RecyclerView>(R.id.student_recycler).apply {
            layoutManager = LinearLayoutManager(this@MapActivity)
            adapter = studentAdapter
        }


        panelToggle.setOnClickListener { togglePanel() }
        filtersToggle.setOnClickListener { toggleFilters() }

        listOf(searchInput,filterNameInput, filterCodeInput, filterProjectInput).forEach { editText ->
            editText.doAfterTextChanged { applyFilters() }
        }

        val toggleAdvertiseButton = findViewById<Button>(R.id.btnToggleAdvertise)
        toggleAdvertiseButton.text = if (isAdvertising) "Stop Advertising" else "Start Advertising"
        toggleAdvertiseButton.setOnClickListener { toggleAdvertise() }

        val mapContainer = findViewById<ConstraintLayout>(R.id.map_container)
        val collapseIfExpanded: (View) -> Unit = {
            if (isPanelExpanded) {
                collapsePanel()
            }
        }
        mapContainer.setOnClickListener(collapseIfExpanded)
        mapView.setOnClickListener(collapseIfExpanded)

        viewModel.persons.observe(this) { persons ->
            val previousPersons = personsMap
            personsMap = persons
            allStudents = persons.values.filterIsInstance<Student>()
            allStudents.forEach { student ->
                val wasKnownStudent = previousPersons[student.id] is Student
                if (!wasKnownStudent && student.id !in trackedStudentsIds) {
                    trackedStudentsIds.add(student.id)
                }
            }
            applyFilters()
            refreshMapPersons()
        }

        updatePanelUi()
        updateFiltersUi()
    }

    private fun togglePanel() {
        isPanelExpanded = !isPanelExpanded
        if (!isPanelExpanded) {
            areFiltersExpanded = false
        }
        updatePanelUi()
        updateFiltersUi()
    }
    private fun collapsePanel() {
        isPanelExpanded = false
        areFiltersExpanded = false
        updatePanelUi()
        updateFiltersUi()
    }
    private fun toggleFilters() {
        if (!isPanelExpanded) return
        areFiltersExpanded = !areFiltersExpanded
        updateFiltersUi()
    }

    private fun updatePanelUi() {
        val params = panelCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = 0
        params.matchConstraintPercentWidth = if (isPanelExpanded) 0.75f else 0f
        panelCard.layoutParams = params
        panelCard.isVisible = isPanelExpanded

        panelContent.isVisible = isPanelExpanded
        panelTitle.isVisible = isPanelExpanded
        filtersToggle.isVisible = isPanelExpanded
        panelToggleHint.isVisible = !isPanelExpanded
        panelToggle.setImageResource(
            if (isPanelExpanded) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next
        )

        panelToggle.contentDescription = getString(
            if (isPanelExpanded) {
                R.string.panel_toggle_content_description_collapse
            } else {
                R.string.panel_toggle_content_description_expand
            }
        )
    }

    private fun updateFiltersUi() {
        filtersContainer.isVisible = areFiltersExpanded && isPanelExpanded
        filtersToggle.rotation = if (areFiltersExpanded && isPanelExpanded) 180f else 0f
    }

    private fun applyFilters() {
        val searchQuery = searchInput.text?.toString().orEmpty().trim()
        val nameQuery = filterNameInput.text?.toString().orEmpty().trim()
        val codeQuery = filterCodeInput.text?.toString().orEmpty().trim()
        val projectQuery = filterProjectInput.text?.toString().orEmpty().trim()

        val filtered = allStudents.filter { student ->
            matchesSearch(student, searchQuery) &&
                    matchesField(student.name,nameQuery) &&
                    matchesField(student.institutionalCode, codeQuery) &&
                    matchesField(student.projectName, projectQuery)
        }.sortedBy { it.name ?: it.username }

        val items = filtered.map { student ->
            val isTracked = trackedStudentsIds.contains(student.id)
            StudentAdapter.StudentListItem(student, isTracked)
        }

        studentAdapter.submitList(items)
    }

    private fun matchesSearch(student: Student, query: String): Boolean {
        if (query.isEmpty()) return true
        return listOfNotNull(
            student.institutionalCode,
            student.name,
            student.projectName,
            student.username
        ).any { it.contains(query, ignoreCase = true) }
    }

    private fun matchesField(value: String?, query: String): Boolean {
        if (query.isEmpty()) return true
        return value?.contains(query, ignoreCase = true) == true
    }

    private fun toggleAdvertise() {
        isAdvertising = !isAdvertising
        val toggleAdvertiseButton = findViewById<Button>(R.id.btnToggleAdvertise)
        toggleAdvertiseButton.text = if (isAdvertising) "Stop Advertising" else "Start Advertising"

        val action = if (isAdvertising) BleAdvertiserService.ACTION_START else BleAdvertiserService.ACTION_STOP
        val svcIntent = Intent(this, BleAdvertiserService::class.java).apply {
            this.action = action
        }

        if (isAdvertising) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            stopService(svcIntent)
        }
    }

    private fun refreshMapPersons() {
        val personsToDisplay = personsMap.values.filter { person ->
            when (person) {
                is Student -> trackedStudentsIds.contains(person.id)
                else -> true
            }
        }
        mapView.setPersons(personsToDisplay)
    }
}