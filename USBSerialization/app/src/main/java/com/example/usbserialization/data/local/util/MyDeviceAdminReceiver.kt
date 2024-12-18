package com.example.usbserialization.data.local.util
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
/**
 * @author madhu.kumar
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
