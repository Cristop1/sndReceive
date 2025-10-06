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
import androidx.lifecycle.lifecycleScope
import com.cuibluetooth.bleeconomy.repository.SessionStore

import kotlinx.coroutines.launch

// Services
import com.cuibluetooth.bleeconomy.service.ImuPublisherService

class MainActivity : AppCompatActivity() {

    private lateinit var sessionStore: SessionStore
    private var activeSession: SessionStore.Session? = null
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
            if (hasAllPerms()) ensureSessionOrLogin()
            else finish()
        }

    private val requestEnableBT =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            proceed(activeSession)
        }

    private val requestLogin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result->
            if (result.resultCode == RESULT_OK) {
                lifecycleScope.launch{
                    val session = sessionStore.getSession()
                    if(session == null || sessionStore.isSessionExpired(session)){
                        finish()
                    } else{
                        activeSession = session
                        proceed(session)
                    }
                }
            } else {
                finish()
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore.getInstance(applicationContext)
        // No UI needed; we’re a trampoline to the map & service.
        if (!hasAllPerms()) {
            requestPerms.launch(requiredPerms)
        } else {
            ensureSessionOrLogin()
        }
    }

    private fun ensureSessionOrLogin() {
        lifecycleScope.launch {
            val session = sessionStore.getSession()
            if (sessionStore.isSessionExpired(session)) {
                sessionStore.clearSession()
                activeSession = null
                requestLogin.launch(Intent(this@MainActivity, LoginActivity::class.java))
            } else if (session == null) {
                activeSession = null
                requestLogin.launch(Intent(this@MainActivity, LoginActivity::class.java))
            } else {
                activeSession = session
                proceed(session)
            }
        }
    }

    private fun proceed(session : SessionStore.Session? = activeSession) {
        val currentSession = session ?: run {
            ensureSessionOrLogin()
            return
        }
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
            // Optional defaults, can be omitted
            putExtra(BleAdvertiserService.EXTRA_FREQ_HZ, 1.0)
            putExtra(BleAdvertiserService.EXTRA_DUTY_PCT, 50)
            putExtra(BleAdvertiserService.EXTRA_UUID_STR, currentSession.assignedUuid)
            putExtra(BleAdvertiserService.EXTRA_PAYLOAD, currentSession.permitCode)
        }
        ContextCompat.startForegroundService(this, svcIntent)

        startService(Intent(this, ImuPublisherService::class.java))
        
        // Hop to MapActivity
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }

    private fun hasAllPerms(): Boolean =
        requiredPerms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}