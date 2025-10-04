package com.cuibluetooth.bleeconomy

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPerms: Array<String> by lazy {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base + Manifest.permission.POST_NOTIFICATIONS
        } else base
    }

    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // If all granted, proceed; else just finish (or show a message if you prefer)
            if (hasAllPerms()) proceed()
            else finish()
        }

    private val requestEnableBT =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            proceed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No UI needed; we’re a trampoline to the map & service.
        if (!hasAllPerms()) {
            requestPerms.launch(requiredPerms)
        } else {
            proceed()
        }
    }

    private fun proceed() {
        // Ensure Bluetooth is enabled
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btMgr.adapter
        if (adapter == null) {
            // Device lacks Bluetooth; bail gracefully
            finish()
            return
        }
        if (!adapter.isEnabled) {
            requestEnableBT.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Start foreground advertiser service
        val svcIntent = Intent(this, BleAdvertiserService::class.java).apply {
            action = BleAdvertiserService.ACTION_START
            // Optional defaults—you can remove or customize these:
            putExtra(BleAdvertiserService.EXTRA_FREQ_HZ, 1.0)
            putExtra(BleAdvertiserService.EXTRA_DUTY_PCT, 50)
            putExtra(BleAdvertiserService.EXTRA_UUID_STR, "0000c111-0000-1000-8000-00805f9b34fb")
            putExtra(BleAdvertiserService.EXTRA_PAYLOAD, "ping")
        }
        ContextCompat.startForegroundService(this, svcIntent)

        // Hop to MapActivity
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }

    private fun hasAllPerms(): Boolean =
        requiredPerms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}