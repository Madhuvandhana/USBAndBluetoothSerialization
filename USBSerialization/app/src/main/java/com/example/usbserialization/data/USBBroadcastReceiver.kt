package com.example.usbserialization.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.example.usbserialization.presentation.Constants.INTENT_ACTION_GRANT_USB

/**
 * @author madhu.kumar
 * Created 12/27/23 at 11:36 AM
 */
class USBBroadcastReceiver(private val onUSBAccessGranted: (Boolean) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (INTENT_ACTION_GRANT_USB == intent?.action) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            onUSBAccessGranted(granted)
        }
    }
}
