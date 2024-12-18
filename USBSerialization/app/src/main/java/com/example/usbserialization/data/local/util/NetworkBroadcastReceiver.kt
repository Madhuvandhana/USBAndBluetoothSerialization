package com.example.usbserialization.data.local.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * @author madhu.kumar
 */
class NetworkBroadcastReceiver(
    private val onNetworkDataReceived: (Boolean, String, String, String, String, String, List<String>?) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent != null) {
            val dhcpEnabled = intent.getBooleanExtra(DHCP_ENABLED_PARAM, false)
            val ipAddress = intent.getStringExtra(IP_ADDRESS_PARAM) ?: "0.0.0.0"
            val gateway = intent.getStringExtra(GATEWAY_PARAM) ?: ""
            val dns1 = intent.getStringExtra(DNS1_PARAM) ?: ""
            val dns2 = intent.getStringExtra(DNS2_PARAM) ?: ""
            val subnetMask = intent.getStringExtra(SUBNET_MASK_PARAM) ?: ""
            val ssidList: ArrayList<String>? = intent.getStringArrayListExtra(SSID_LIST_PARAM)
            onNetworkDataReceived(dhcpEnabled, ipAddress, gateway, dns1, dns2, subnetMask, ssidList)
        }
    }
}