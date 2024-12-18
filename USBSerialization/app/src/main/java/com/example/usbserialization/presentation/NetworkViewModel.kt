package com.example.usbserialization.presentation
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.usbserialization.data.local.FormData
import com.example.usbserialization.data.local.util.DHCP_ENABLED_PARAM
import com.example.usbserialization.data.local.util.DNS1_PARAM
import com.example.usbserialization.data.local.util.DNS2_PARAM
import com.example.usbserialization.data.local.util.FALLBACK_SSID_PARAM
import com.example.usbserialization.data.local.util.GATEWAY_PARAM
import com.example.usbserialization.data.local.util.IP_ADDRESS_PARAM
import com.example.usbserialization.data.local.util.SSID_LIST_PARAM
import com.example.usbserialization.data.local.util.PRIMARY_SSID_PARAM
import com.example.usbserialization.data.local.util.SUBNET_MASK_PARAM
import com.example.usbserialization.domain.FormDataRepository
import com.example.usbserialization.domain.NetworkCallbackHandler
import com.example.usbserialization.domain.WebServerRepository
import com.example.usbserialization.domain.model.NetworkSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * @author madhu.kumar
 */
private const val SERVER_STOP_DELAY_MILLIS = 60000L
private const val SELECT_A_NETWORK = "Select a network"

@HiltViewModel
class NetworkViewModel
    @Inject
    constructor(
        private val formDataRepository: FormDataRepository,
        private val webServerRepository: WebServerRepository,
        private val networkStateReceiver: NetworkCallbackHandler,
    ) : ViewModel() {
        private val _networkSettings = MutableStateFlow(NetworkSettings())
        val networkSettings: StateFlow<NetworkSettings> get() = _networkSettings

        init {
            fetchAllFormData()
        }

        private val errorHandler =
            CoroutineExceptionHandler { _, exception ->
                Log.e("NetworkViewModel", "CoroutineException: " + exception.message)
            }

        private fun fetchAllFormData() {
            viewModelScope.launch {
                formDataRepository.getAllFormData().collect { formData ->
                    // Log the fetched form data
                    Log.d("NetworkViewModel", "Fetched FormData: $formData")
                }
            }
        }

        fun fetchNetworkSettings(
            dhcpEnabled: Boolean,
            ipAddress: String,
            gateway: String,
            dns1: String,
            dns2: String,
            subnetMask: String,
            ssidList: List<String>?,
        ) {
            viewModelScope.launch(errorHandler) {
                val settings =
                    NetworkSettings(
                        dhcpEnabled = dhcpEnabled,
                        ipAddress = ipAddress,
                        subnetMask = subnetMask,
                        gateway = gateway,
                        dns1 = dns1,
                        dns2 = dns2,
                        ssidList = ssidList ?: emptyList(),
                        selectedPrimarySsid = ssidList?.firstOrNull() ?: SELECT_A_NETWORK,
                    )

                _networkSettings.value = settings
            }
        }

        internal fun saveFormData(networkSettings: NetworkSettings) {
            viewModelScope.launch(errorHandler) {
                val dhcpEnabled = networkSettings.dhcpEnabled
                val ipAddress = networkSettings.ipAddress
                val subnetMask = networkSettings.subnetMask
                val gateway = networkSettings.gateway
                val dns1 = networkSettings.dns1
                val dns2 = networkSettings.dns2
                val primarySsid = networkSettings.selectedPrimarySsid.replace("^\"|\"$".toRegex(), "")
                val secondarySsid = networkSettings.selectedSecondarySsid.replace("^\"|\"$".toRegex(), "")
                val primarySsidPassword = networkSettings.primarySsidPassword
                val secondarySsidPassword = networkSettings.secondarySsidPassword

                val formDataEntity =
                    FormData(
                        dhcpEnabled = dhcpEnabled,
                        ipAddress = ipAddress,
                        subnetMask = subnetMask,
                        gateway = gateway,
                        dns1 = dns1,
                        dns2 = dns2,
                        primarySsid = primarySsid,
                        secondarySsid = secondarySsid,
                        primarySsidPassword = primarySsidPassword,
                        secondarySsidPassword = secondarySsidPassword,
                    )

                formDataRepository.insertFormData(formDataEntity)
            }
        }

        internal fun startWebServer() {
            viewModelScope.launch(errorHandler) {
                try {
                    webServerRepository.startWebServer()
                    Log.d("NetworkViewModel", "Web server started on port 8080")
                } catch (ioe: IOException) {
                    Log.e("NetworkViewModel", "The server could not start: ${ioe.message}")
                } catch (e: Exception) {
                    Log.e("NetworkViewModel", "An error occurred: ${e.message}+ ${e.localizedMessage}")
                }

                // Stop the server in case back is pressed after an interval
                delay(SERVER_STOP_DELAY_MILLIS)
                stopWebServer()
            }
        }

        internal fun stopWebServer() {
            viewModelScope.launch {
                try {
                    webServerRepository.stopWebServer()
                    Log.d("NetworkViewModel", "Web server stopped")
                } catch (e: Exception) {
                    Log.e("NetworkViewModel", "Error stopping server: ${e.message}")
                } finally {
                    resetViewModelScope() // Reset after stopping the server
                }
            }
        }

        private fun resetViewModelScope() {
            viewModelScope.coroutineContext.cancelChildren()
        }

        internal fun getFormUrl(settings: NetworkSettings): String {
            val ipAddress = settings.ipAddress
            // To Replace the url with localhost uncomment  below
            // val uri = Uri.parse("http://localhost:8080/form.html")
            val uri = Uri.parse("https://$ipAddress:8443/form.html")
            return uri
                .buildUpon()
                .appendQueryParameter(DHCP_ENABLED_PARAM, settings.dhcpEnabled.toString())
                .appendQueryParameter(IP_ADDRESS_PARAM, settings.ipAddress)
                .appendQueryParameter(SUBNET_MASK_PARAM, settings.subnetMask)
                .appendQueryParameter(GATEWAY_PARAM, settings.gateway)
                .appendQueryParameter(DNS1_PARAM, settings.dns1)
                .appendQueryParameter(DNS2_PARAM, settings.dns2)
                .appendQueryParameter(PRIMARY_SSID_PARAM, settings.selectedPrimarySsid)
                .appendQueryParameter(FALLBACK_SSID_PARAM, settings.selectedSecondarySsid)
                .appendQueryParameter(SSID_LIST_PARAM, settings.ssidList.joinToString(","))
                .build()
                .toString()
        }

        internal fun registerNetworkCallback() {
            networkStateReceiver.registerNetworkCallback()
        }

        internal fun unregisterNetworkCallback() {
            networkStateReceiver.unregisterNetworkCallback()
        }
    }
