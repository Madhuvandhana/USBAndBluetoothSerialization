package com.example.usbserialization.data.local.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.usbserialization.di.IoDispatcher
import com.example.usbserialization.domain.FormDataRepository
import com.example.usbserialization.domain.NetworkCallbackHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

private const val PREFIX_LENGTH = 24
private const val FULL_MASK = 0xffffffff.toInt()
private const val BYTE_MASK = 0xff
private const val SHIFT_32 = 32
private const val SHIFT_24 = 24
private const val SHIFT_16 = 16
private const val SHIFT_8 = 8
private val LOCATION_PERMISSION_REQUEST_CODE = 1001

/**
 * @author madhu.kumar
 */
class NetworkStateReceiver
    @Inject
    constructor(
        private val context: Context,
        @IoDispatcher private val coroutineDispatcher: CoroutineDispatcher,
        private val externalScope: CoroutineScope,
        private val formDataRepository: FormDataRepository,
    ) : NetworkCallbackHandler {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        private val maxPrimaryAttempts = 5

        override fun registerNetworkCallback() {
            val builder = NetworkRequest.Builder()
            connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
        }

        override fun unregisterNetworkCallback() {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }

        private val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    super.onCapabilitiesChanged(network, capabilities)
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    logNetworkDetails(linkProperties, capabilities)
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    super.onLinkPropertiesChanged(network, linkProperties)
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    logNetworkDetails(linkProperties, capabilities)
                }

                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("NetworkStateReceiver", "Network disconnected")
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    externalScope.launch {
                        var primarySsid: String? = capabilities?.let {
                            getAvailableSsids(context, it)?.firstOrNull()
                        }
                        var fallbackSsid: String? = capabilities?.let {
                            getAvailableSsids(context, it)?.elementAtOrNull(1)
                        }
                        var primarySsidPassword: String? = null
                        var secondarySsidpassword: String? = null
                        val formDataList = formDataRepository.getAllFormData().firstOrNull()
                        formDataList?.lastOrNull()?.let { formData ->
                            primarySsid = formData.primarySsid ?: primarySsid
                            fallbackSsid = formData.secondarySsid ?: fallbackSsid
                            primarySsidPassword = formData.primarySsidPassword?.let { decryptPassword(it) }
                            secondarySsidpassword = formData.secondarySsidPassword?.let { decryptPassword(it) }
                        }

                        connectToSsids(primarySsid ?: UNKNOWN, primarySsidPassword, fallbackSsid ?: UNKNOWN, secondarySsidpassword)
                    }
                }
            }

        private fun logNetworkDetails(
            linkProperties: LinkProperties?,
            networkCapabilities: NetworkCapabilities?,
        ) {
            if (networkCapabilities != null && linkProperties != null) {
                val ssidList = getAvailableSsids(context, networkCapabilities)

                Log.d("NetworkStateReceiver", "Available SSIDs: ${ssidList?.joinToString(", ")}")
                val dhcpEnabled = !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                val inetAddress =
                    linkProperties.linkAddresses
                        .map { it.address }
                        .firstOrNull { it is Inet4Address }

                val ipAddress = inetAddress?.let { convertInetAddressToString(it) } ?: "0.0.0.0"
                val gateway =
                    linkProperties.routes
                        .firstOrNull { it.gateway != null }
                        ?.gateway
                        ?.hostAddress ?: ""
                val dns1 = linkProperties.dnsServers.getOrNull(0)?.hostAddress ?: ""
                val dns2 = linkProperties.dnsServers.getOrNull(1)?.hostAddress ?: ""
                val prefixLength =
                    linkProperties.linkAddresses.firstOrNull()?.prefixLength ?: PREFIX_LENGTH
                val subnetMask = calculateSubnetMask(prefixLength)

                Log.d("NetworkStateReceiver", "DHCP enabled: $dhcpEnabled")
                Log.d("NetworkStateReceiver", "IP Address: $ipAddress")
                Log.d("NetworkStateReceiver", "Gateway: $gateway")
                Log.d("NetworkStateReceiver", "DNS 1: $dns1")
                Log.d("NetworkStateReceiver", "DNS 2: $dns2")
                Log.d("NetworkStateReceiver", "Subnet Mask: $subnetMask")

                sendNetworkInfo(dhcpEnabled, ipAddress, gateway, dns1, dns2, subnetMask, ssidList)
            } else {
                Log.d("NetworkStateReceiver", "No active network connection")
            }
        }

        private fun getAvailableSsids(
            context: Context,
            networkCapabilities: NetworkCapabilities,
        ): List<String>? {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val scanResults: List<ScanResult> = wifiManager.scanResults
                    val ssidList =
                        scanResults
                            .map {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    it.wifiSsid?.toString() ?: ""
                                } else {
                                    it.SSID ?: ""
                                }
                            }.filter { it.isNotEmpty() }

                    return ssidList
                } else {
                    Log.d("NetworkStateReceiver", "Location permission not granted.")
                    return null
                }
            }
            return null
        }

        private fun sendNetworkInfo(
            dhcpEnabled: Boolean,
            ipAddress: String,
            gateway: String,
            dns1: String,
            dns2: String,
            subnetMask: String,
            ssid: List<String>?,
        ) {
            val intent =
                Intent(NETWORK_INFO_UPDATED).apply {
                    putExtra(DHCP_ENABLED_PARAM, dhcpEnabled)
                    putExtra(IP_ADDRESS_PARAM, ipAddress)
                    putExtra(GATEWAY_PARAM, gateway)
                    putExtra(DNS1_PARAM, dns1)
                    putExtra(DNS2_PARAM, dns2)
                    putExtra(SUBNET_MASK_PARAM, subnetMask)
                    putStringArrayListExtra(SSID_LIST_PARAM, ArrayList(ssid ?: emptyList()))
                }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        private fun convertInetAddressToString(inetAddress: InetAddress): String {
            val addressBytes = inetAddress.address
            val buffer = ByteBuffer.wrap(addressBytes)
            return "${buffer.get().toInt() and BYTE_MASK}" +
                ".${buffer.get().toInt() and BYTE_MASK}" +
                ".${buffer.get().toInt() and BYTE_MASK}" +
                ".${buffer.get().toInt() and BYTE_MASK}"
        }

        private fun calculateSubnetMask(prefixLength: Int): String {
            val mask = FULL_MASK shl (SHIFT_32 - prefixLength)
            return (
                (mask shr SHIFT_24 and BYTE_MASK).toString() + "." +
                    (mask shr SHIFT_16 and BYTE_MASK).toString() + "." +
                    (mask shr SHIFT_8 and BYTE_MASK).toString() + "." +
                    (mask and BYTE_MASK).toString()
            )
        }

        /**
         * Attempts to connect to the primary SSID. If connection fails after multiple attempts,
         * it will attempt to connect to the fallback SSID with a 30-second timeout.
         * If both connections fail, it checks for an active Ethernet or cellular connection.
         *
         * @param primarySsid The primary Wi-Fi SSID to connect to.
         * @param primaryPassword The password for the primary SSID (if applicable).
         * @param fallbackSsid The fallback Wi-Fi SSID to connect to in case of primary SSID failure.
         * @param fallbackPassword The password for the fallback SSID (if applicable).
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        suspend fun connectToSsids(
            primarySsid: String,
            primaryPassword: String?,
            fallbackSsid: String,
            fallbackPassword: String?,
        ) {
            val connectedToPrimary = attemptConnectToPrimarySsid(primarySsid, primaryPassword)

            if (!connectedToPrimary) {
                val connectedToFallback = attemptConnectToFallbackSsid(fallbackSsid, fallbackPassword)

                if (connectedToFallback) {
                    return
                }
            } else {
                return
            }
        }

        /**
         * Attempts to connect to the primary SSID with multiple retries (up to [maxPrimaryAttempts]).
         *
         * @param primarySsid The primary Wi-Fi SSID to connect to.
         * @param primaryPassword The password for the primary SSID (if applicable).
         * @return True if the connection is successful, false if all attempts fail.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private suspend fun attemptConnectToPrimarySsid(
            primarySsid: String,
            primaryPassword: String?,
        ): Boolean {
            for (attempt in 1..maxPrimaryAttempts) {
                Log.d("NetworkStateReceiver", "Attempting to connect to primary SSID: $primarySsid (Attempt $attempt)")

                if (checkForEthernetOrCellular()) {
                    Log.d("NetworkStateReceiver", "Ethernet or cellular connection detected, stopping Wi-Fi connection attempts.")
                    externalScope.cancel()
                    return true
                }

                val primarySuggestion = createWifiNetworkSuggestion(primarySsid, primaryPassword)
                addWifiSuggestions(listOf(primarySuggestion))

                val connected = checkNetworkConnection(primarySsid, timeoutMs = 10000) // 10-second timeout for each attempt

                if (connected) {
                    Log.d("NetworkStateReceiver", "Connected to primary SSID: $primarySsid")
                    return true
                } else {
                    Log.d("NetworkStateReceiver", "Failed to connect to primary SSID: $primarySsid (Attempt $attempt)")
                }
            }
            Log.d("NetworkStateReceiver", "Exceeded max attempts for primary SSID: $primarySsid")
            return false
        }

        /**
         * Attempts to connect to the fallback SSID with a 30-second timeout.
         *
         * @param fallbackSsid The fallback Wi-Fi SSID to connect to.
         * @param fallbackPassword The password for the fallback SSID (if applicable).
         * @return True if the connection is successful, false if the attempt times out or fails.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private suspend fun attemptConnectToFallbackSsid(
            fallbackSsid: String,
            fallbackPassword: String?,
        ): Boolean {
            Log.d("NetworkStateReceiver", "Attempting to connect to fallback SSID: $fallbackSsid")

            val result =
                withTimeoutOrNull(30000) {
                    if (checkForEthernetOrCellular()) {
                        Log.d("NetworkStateReceiver", "Ethernet or cellular connection detected, stopping Wi-Fi connection attempts.")
                        externalScope.cancel()
                        return@withTimeoutOrNull true
                    }
                    val fallbackSuggestion = createWifiNetworkSuggestion(fallbackSsid, fallbackPassword)
                    addWifiSuggestions(listOf(fallbackSuggestion))

                    checkNetworkConnection(fallbackSsid, timeoutMs = 30000)
                }

            return if (result == true) {
                Log.d("NetworkStateReceiver", "Connected to fallback SSID: $fallbackSsid")
                true
            } else {
                Log.d("NetworkStateReceiver", "Failed to connect to fallback SSID: $fallbackSsid within timeout")
                false
            }
        }

        /**
         * Checks for an active Ethernet or cellular connection.
         * Logs the result and cancels Wi-Fi fallback mechanism if such a connection exists.
         *
         * @return True if Ethernet or cellular connection is active, false otherwise.
         */
        private fun checkForEthernetOrCellular(): Boolean {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        Log.d("NetworkStateReceiver", "Ethernet connection is active. Cancelling Wi-Fi fallback attempts.")
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        Log.d("NetworkStateReceiver", "Cellular connection is active. Cancelling Wi-Fi fallback attempts.")
                        return true
                    }
                    else -> {
                        Log.d("NetworkStateReceiver", "No Ethernet or cellular connection found.")
                        return false
                    }
                }
            } else {
                Log.d("NetworkStateReceiver", "No active network connection found.")
                return false
            }
        }

        /**
         * Continuously checks for connection success to the specified SSID within a given timeout.
         * The function checks every second for connection status and stops early if connected.
         *
         * @param ssid The SSID to check for a connection to.
         * @param timeoutMs The maximum time to wait for a successful connection, in milliseconds.
         * @return True if connected to the SSID, false if the timeout is reached without a connection.
         */
        private suspend fun checkNetworkConnection(
            ssid: String,
            timeoutMs: Long,
        ): Boolean =
            withTimeoutOrNull(timeoutMs) {
                var connected = false
                while (!connected) {
                    connected = isConnectedToNetwork(ssid)
                    if (connected) break
                    delay(1000)
                }
                connected
            } ?: false

        /**
         * Creates a [WifiNetworkSuggestion] for a given SSID and password.
         *
         * @param ssid The SSID of the Wi-Fi network.
         * @param password The password for the Wi-Fi network (if applicable).
         * @return A configured [WifiNetworkSuggestion].
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun createWifiNetworkSuggestion(
            ssid: String,
            password: String?,
        ): WifiNetworkSuggestion =
            WifiNetworkSuggestion
                .Builder()
                .setSsid(ssid)
                .apply {
                    if (!password.isNullOrEmpty()) {
                        setWpa2Passphrase(password) // Use WPA2 passphrase if available
                    }
                }.build()

        /**
         * Adds Wi-Fi network suggestions to the system. This is required to connect to networks programmatically.
         *
         * @param suggestions A list of [WifiNetworkSuggestion] objects for available networks.
         */
        private fun addWifiSuggestions(suggestions: List<WifiNetworkSuggestion>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val status = wifiManager.addNetworkSuggestions(suggestions)
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    Log.d("NetworkStateReceiver", "Wi-Fi suggestions added successfully.")
                } else {
                    Log.d("NetworkStateReceiver", "Failed to add Wi-Fi suggestions, status code: $status")
                }
            } else {
                Log.d("NetworkStateReceiver", "Wi-Fi suggestions API not supported on this Android version.")
            }
        }

        private fun isConnectedToNetwork(ssid: String): Boolean {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true && getConnectedSsid() == ssid
        }

        private fun getConnectedSsid(): String? {
            val wifiInfo = wifiManager.connectionInfo
            return if (wifiInfo != null && wifiInfo.ssid != WifiManager.UNKNOWN_SSID) {
                wifiInfo.ssid.replace("\"", "") // Remove quotes around the SSID
            } else {
                null
            }
        }
    }
