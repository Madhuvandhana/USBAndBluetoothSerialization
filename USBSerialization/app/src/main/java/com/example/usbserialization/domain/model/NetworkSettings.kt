package com.example.usbserialization.domain.model

/**
 * @author madhu.kumar
 * Created 5/16/24 at 12:13 PM
 */
data class NetworkSettings(
    val ipAddress: String = "",
    val dhcpEnabled: Boolean = false,
    val subnetMask: String = "",
    val gateway: String = "",
    val dns1: String = "",
    val dns2: String = "",
    val selectedPrimarySsid: String = "",
    val selectedSecondarySsid: String = "",
    val primarySsidPassword: String = "",
    val secondarySsidPassword: String = "",
    val ssidList: List<String> = emptyList(),
)
