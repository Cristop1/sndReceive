package com.cuibluetooth.bleeconomy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cuibluetooth.bleeconomy.model.Person
import com.cuibluetooth.bleeconomy.model.PersonId
import com.cuibluetooth.bleeconomy.model.Student
import com.cuibluetooth.bleeconomy.repository.SessionStore
import com.cuibluetooth.bleeconomy.ui.MapView
import com.cuibluetooth.bleeconomy.ui.StudentAdapter
import com.cuibluetooth.bleeconomy.viewmodel.MapViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
    private lateinit var panelCard: MaterialCardView
    private lateinit var selectAllButton : MaterialButton
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
    private lateinit var signOutButton: MaterialButton

    private var isPanelExpanded = false
    private var areFiltersExpanded = false
    private var allStudents: List<Student> = emptyList()
    private val trackedStudentsIds : MutableSet<PersonId> = mutableSetOf()
    private var personsMap: Map<PersonId, Person> = emptyMap()
    private var isAdvertising = true
    private var toggleAdvertiseButton : Button? = null
    private var panelToggleContainer : View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map_view)
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        toggleAdvertiseButton = findViewById(R.id.btnToggleAdvertise)
        panelCard = findViewById(R.id.panel_container)
        panelToggle = findViewById(R.id.btn_panel_fold)
        panelTitle = findViewById(R.id.panel_title)
        selectAllButton = findViewById(R.id.btn_toggle_select_all)
        filtersToggle = findViewById(R.id.btn_filters_toggle)
        panelContent = findViewById(R.id.panel_content_container)
        filtersContainer = findViewById(R.id.filter_fields_container)
        panelToggleHint = findViewById(R.id.panel_toggle_hint)
        panelToggleContainer = findViewById(R.id.panel_toggle_container)
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
            updateSelectAllButton()
            applyFilters()
            refreshMapPersons()
        }
        val studentRecycler = findViewById<RecyclerView>(R.id.student_recycler).apply {
            layoutManager = LinearLayoutManager(this@MapActivity)
            adapter = studentAdapter
        }


        panelToggle.setOnClickListener { togglePanel(toggleAdvertiseButton, signOutButton, panelToggleContainer) }
        filtersToggle.setOnClickListener { toggleFilters() }
        selectAllButton.setOnClickListener { toggleSelectAllStudents() }

        listOf(searchInput,filterNameInput, filterCodeInput, filterProjectInput).forEach { editText ->
            editText.doAfterTextChanged { applyFilters() }
        }

        toggleAdvertiseButton = findViewById<Button>(R.id.btnToggleAdvertise)
        toggleAdvertiseButton?.bringToFront()
        toggleAdvertiseButton?.translationZ = 16f
        toggleAdvertiseButton?.text = if (isAdvertising) "Stop Advertising" else "Start Advertising"
        toggleAdvertiseButton?.setOnClickListener { toggleAdvertise() }

        signOutButton = findViewById(R.id.btn_sign_out)
        signOutButton.translationZ = 16f
        signOutButton.setOnClickListener {
            lifecycleScope.launch {
                SessionStore.getInstance(applicationContext).clearSession()
                val intent = Intent(this@MapActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                finish()
            }
        }

        val mapContainer = findViewById<ConstraintLayout>(R.id.map_container)
        val collapseIfExpanded: (View) -> Unit = {
            if (isPanelExpanded) {
                collapsePanel(toggleAdvertiseButton, signOutButton)
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
            updateSelectAllButton()
        }

        updatePanelUi(toggleAdvertiseButton, signOutButton, panelToggleContainer)
        updateFiltersUi()
        updateSelectAllButton()
    }

    private fun togglePanel(toggleAdvertiseButton: Button?, signOutButton : MaterialButton?,panelToggleContainer : View?) {
        isPanelExpanded = !isPanelExpanded
        if (!isPanelExpanded) {
            areFiltersExpanded = false
        }
        updatePanelUi(toggleAdvertiseButton, signOutButton, panelToggleContainer)
        updateFiltersUi()
    }
    private fun collapsePanel(toggleAdvertiseButton: Button?, signOutButton: MaterialButton?) {
        isPanelExpanded = false
        areFiltersExpanded = false
        updatePanelUi(toggleAdvertiseButton, signOutButton, panelToggleContainer)
        updateFiltersUi()
    }
    private fun toggleFilters() {
        if (!isPanelExpanded) return
        areFiltersExpanded = !areFiltersExpanded
        updateFiltersUi()
    }

    private fun updatePanelUi(toggleAdvertiseButton: Button?, signOutButton : MaterialButton?, panelToggleContainer : View?) {
        val params = panelCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = 0
        params.matchConstraintPercentWidth = if (isPanelExpanded) 0.75f else 0f
        panelCard.layoutParams = params
        panelCard.isVisible = isPanelExpanded

        panelContent.isVisible = isPanelExpanded
        panelTitle.isVisible = isPanelExpanded
        filtersToggle.isVisible = isPanelExpanded
        panelToggleHint.isVisible = !isPanelExpanded

        toggleAdvertiseButton?.isVisible = !isPanelExpanded
        toggleAdvertiseButton?.isEnabled = !isPanelExpanded

        signOutButton?.isVisible = isPanelExpanded
        signOutButton?.isEnabled = isPanelExpanded

        panelToggleContainer?.isVisible = !isPanelExpanded
        panelToggleContainer?.isEnabled = !isPanelExpanded

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
        updateSelectAllButton()
    }

    private fun toggleSelectAllStudents(){
        val shouldSelectAll = !allStudents.all { trackedStudentsIds.contains(it.id) }
        if(shouldSelectAll) {
            trackedStudentsIds.addAll(allStudents.map { it.id })
        } else{
            trackedStudentsIds.removeAll(allStudents.map { it.id } )
        }
        applyFilters()
        refreshMapPersons()
        updateSelectAllButton()
    }

    private fun updateSelectAllButton(){
        val allTracked = allStudents.all { trackedStudentsIds.contains(it.id) }
        val textRes = if (allTracked) R.string.deselect_all else R.string.select_all
        if (::selectAllButton.isInitialized) {
            selectAllButton.setText(textRes)
        }
    }
    private fun containsSubsequence(text: String?, query: String): Boolean {
        if (text == null) return false
        if (query.isEmpty()) return true
        val pattern = query.map { Regex.escape(it.toString()) }.joinToString(".*")
        return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    private fun matchOneField(value: String?, token: String): Boolean {
        // 1) normal substring first (fast path)  2) subsequence fallback
        if (value == null) return false
        if (token.isEmpty()) return true
        return value.contains(token, ignoreCase = true) || containsSubsequence(value, token)
    }

    private fun splitTokens(q: String): List<String> =
        q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun matchesSearch(student: Student, query: String): Boolean {
        val tokens = splitTokens(query)
        if (tokens.isEmpty()) return true
        // AND semantics across tokens; token can match any field
        return tokens.all { token ->
            listOfNotNull(
                student.institutionalCode,
                student.name,
                student.projectName
            ).any { field -> matchOneField(field, token) }
        }
    }

    private fun matchesField(value: String?, query: String): Boolean {
        val tokens = splitTokens(query)
        if (tokens.isEmpty()) return true
        return tokens.all { token -> matchOneField(value, token) }
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