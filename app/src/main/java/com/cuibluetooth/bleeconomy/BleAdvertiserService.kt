package com.cuibluetooth.bleeconomy

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import com.cuibluetooth.bleeconomy.model.MyAdvertiseCallback

class BleAdvertiserService : Service() {

    companion object{
        //Actions
        const val ACTION_START = "com.cuibluetooth.bleeconomy.BLE_ADV_START"
        const val ACTION_UPDATE = "com.cuibluetooth.bleeconomy.BLE_ADV_UPDATE"
        const val ACTION_STOP = "com.cuibluetooth.bleeconomy.BLE_ADV_STOP"

        //Extras
        const val EXTRA_FREQ_HZ = "freq_hz" //Double
        const val EXTRA_DUTY_PCT = "duty_pct" //Int
        const val EXTRA_UUID_STR = "uuid_str" //String (UUID)
        const val EXTRA_PAYLOAD = "payload" // String (utf-8)

        // Noti
        private const val NOTI_ID = 42
        private const val NOTI_CHANNEL = "ble_advertiser"

        // Limitations
        private const val MIN_FREQ_HZ = 0.2
        private const val MAX_FREQ_HZ = 20.0

        private const val MIN_ON_MS = 10L //Minimum time on
        private const val MIN_OFF_MS = 0L //Minimum time off
    }

    // Mutable State
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob:Job? = null

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null

    private var currentFreqHz:Double = 1.0
    private var currentDutyPct:Int = 50
    private var currentUuid:UUID = UUID.fromString("0000c111-0000-1000-8000-00805f9b34fb")
    private var currentPayload: ByteArray = "ping".toByteArray(StandardCharsets.UTF_8)

    private val advertiseCallback = MyAdvertiseCallback()


    override fun onCreate(){
        super.onCreate()
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = mgr.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        createNotificationChannel()
        startForeground(NOTI_ID, buildNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action){
            ACTION_START -> {
                readParams(intent)
                startLoop()
            }
            ACTION_UPDATE -> {
                readParams(intent)
                //Only variables update, the loop reads each cycle
                updateNotification()
            }
            ACTION_STOP -> {
                stopLoop()
                stopSelf()
            }
            else-> {
                //If no action on start, loop by default
                startLoop()
            }
        }
        return START_STICKY

    }

    override fun onDestroy() {
        stopLoop()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun readParams(intent: Intent) {
        intent.getDoubleExtra(EXTRA_FREQ_HZ, currentFreqHz).let {
            currentFreqHz = min(MAX_FREQ_HZ, max(MIN_FREQ_HZ, it))
        }
        intent.getIntExtra(EXTRA_DUTY_PCT, currentDutyPct).let {
            currentDutyPct = it.coerceIn(0,100)
        }
        intent.getStringExtra(EXTRA_UUID_STR)?.let run@{ s ->
            try {currentUuid = UUID.fromString(s) } catch (_: Throwable) { return@run }
        }
        intent.getStringExtra(EXTRA_PAYLOAD)?.let run@{ txt ->
            try {currentPayload = txt.toByteArray(StandardCharsets.UTF_8)} catch(_: Throwable){}
        }
    }

    private fun haveRuntimePerms():Boolean{
        return if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    private fun startLoop(){
        if(!haveRuntimePerms()){
            updateNotification("Not enough BLE permits to Advertise. Open the app to concede or go to settings")
            stopSelf()
            return
        }
        if(loopJob?.isActive == true){
            updateNotification()
            return
        }
        loopJob = serviceScope.launch{
            var backoffMs = 50L
            while(isActive){
                if(!bluetoothAdapter.isEnabled || advertiser == null){
                    // Wait until bluetooth is avaliable
                    updateNotification("Bluetooth not avaliable. Retrying...")
                    delay(1000L)
                    continue
                }

                val periodMs = (1000.0/currentFreqHz)
                val onMs = max(MIN_ON_MS, (periodMs * (currentDutyPct / 100.0)).toLong())
                val offMs = max(MIN_OFF_MS, periodMs.toLong() - onMs)

                updateNotification("Advertising @ ${"%.2f".format(currentFreqHz)} Hz (${currentDutyPct}% duty)")

                // Rebuild settings/data every cycle, in case parameters change
                val advData = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid(currentUuid))
                    .addServiceData(ParcelUuid(currentUuid), currentPayload)
                    .build()

                // LOW_LATENCY gives more PDU rate; if freqHz is too high try BALANCED
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false)
                    .setTimeout(0) //manually controlled
                    .build()

                val startedOk = try{
                    advertiser?.startAdvertising(settings, advData, advertiseCallback)
                    true
                } catch (_: SecurityException) {
                    updateNotification("Not enough permits to Advertise")
                    false
                } catch (_: Throwable) {
                    false
                }

                if(startedOk){
                    delay(onMs)
                    try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Throwable) {}
                    if (offMs > 0) delay(offMs)
                    // reset backoff tras un ciclo OK
                    backoffMs = 50L
                } else{
                    delay(backoffMs)
                    backoffMs = min(2000L, backoffMs*2)
                }

            }

            // On exit, make sure the last window is closed
            try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Throwable) {}
        }
    }

    private fun stopLoop(){
        loopJob?.cancel()
        loopJob = null
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Throwable) { }
        updateNotification("Stopped")
    }

    /* === Notification === */
    private fun createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(NOTI_CHANNEL, "BLE advertising", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun stopActionPendingIntent():PendingIntent{
        val stop = Intent(this, BleAdvertiserService::class.java).apply { action = ACTION_STOP }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, 1, stop, flags)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { pi ->
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            PendingIntent.getActivity(this, 0, pi, flags)
        }

        return NotificationCompat.Builder(this, NOTI_CHANNEL)
            .setContentTitle("BLE activo")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground) //TODO change the icon
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Detener", stopActionPendingIntent())
            .build()
    }

    private fun updateNotification(text: String? = null) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shownText = text ?: "Advertising @ ${"%.2f".format(currentFreqHz)} Hz (${currentDutyPct}%), UUID $currentUuid"
        nm.notify(NOTI_ID, buildNotification(shownText))
    }

}
