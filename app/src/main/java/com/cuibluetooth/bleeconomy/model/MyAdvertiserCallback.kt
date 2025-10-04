package com.cuibluetooth.bleeconomy.model
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings

class MyAdvertiseCallback : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
        // Your logic here
    }

    override fun onStartFailure(errorCode: Int) {
        // Your logic here
    }
}