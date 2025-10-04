package com.cuibluetooth.bleeconomy

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.cuibluetooth.bleeconomy.ui.MapView
import com.cuibluetooth.bleeconomy.viewmodel.MapViewModel
import com.cuibluetooth.bleeconomy.R
import android.widget.Button

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
    private var isAdvertising = true  // Assume started from MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map_view)
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]

        // Toggle button
        val toggleAdvertiseButton = findViewById<Button>(R.id.btnToggleAdvertise)
        toggleAdvertiseButton.text = if (isAdvertising) "Stop Advertising" else "Start Advertising"
        toggleAdvertiseButton.setOnClickListener {
            toggleAdvertise()
        }

        viewModel.persons.observe(this) { personsMap ->
            // For now, show all (focus students); later filter
            mapView.setPersons(personsMap.values.toList())
        }

        // Example: Later change reference (e.g., to top-left)
        // mapView.setReferencePixel(x, y)

        // Example: Simulate update after 5s (remove later)
        // Handler(Looper.getMainLooper()).postDelayed({
        //     val newBatch = listOf<Student>(...)  // New positions
        //     viewModel.updatePositions(newBatch)
        // }, 5000)
    }

    private fun toggleAdvertise() {
        isAdvertising = !isAdvertising
        val toggleAdvertiseButton = findViewById<Button>(R.id.btnToggleAdvertise)
        toggleAdvertiseButton.text = if (isAdvertising) "Stop Advertising" else "Start Advertising"

        val action = if (isAdvertising) BleAdvertiserService.ACTION_START else BleAdvertiserService.ACTION_STOP
        val svcIntent = Intent(this, BleAdvertiserService::class.java).apply {
            this.action = action
            // If starting, add any extras (e.g., freq, uuid) from your service defaults
        }

        if (isAdvertising) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            stopService(svcIntent)
        }
    }
}