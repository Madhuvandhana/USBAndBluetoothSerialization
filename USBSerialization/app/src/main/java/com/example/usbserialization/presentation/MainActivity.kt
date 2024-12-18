package com.example.usbserialization.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.usbserialization.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onNewIntent(intent: Intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action) {
            val terminal = supportFragmentManager.findFragmentByTag("terminal") as TerminalFragment?
            terminal?.status("USB device detected")
        }
        super.onNewIntent(intent)
    }

    fun openBluetoothActivity(view: View) {
        startActivity(Intent(this, BluetoothActivity::class.java))
    }

    fun openDeviceActivity(view: View) {
        startActivity(Intent(this, USBDeviceActivity::class.java))
    }
}
