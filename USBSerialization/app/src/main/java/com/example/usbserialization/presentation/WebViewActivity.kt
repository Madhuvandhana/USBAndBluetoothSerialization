package com.example.usbserialization.presentation
import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.usbserialization.R
import com.example.usbserialization.data.local.util.DHCP_ENABLED_PARAM
import com.example.usbserialization.data.local.util.DNS1_PARAM
import com.example.usbserialization.data.local.util.DNS2_PARAM
import com.example.usbserialization.data.local.util.FALLBACK_SSID_PARAM
import com.example.usbserialization.data.local.util.FALLBACK_SSID_PASSWORD_PARAM
import com.example.usbserialization.data.local.util.GATEWAY_PARAM
import com.example.usbserialization.data.local.util.IP_ADDRESS_PARAM
import com.example.usbserialization.data.local.util.MyDeviceAdminReceiver
import com.example.usbserialization.data.local.util.NETWORK_INFO_UPDATED
import com.example.usbserialization.data.local.util.NetworkBroadcastReceiver
import com.example.usbserialization.data.local.util.PRIMARY_SSID_PARAM
import com.example.usbserialization.data.local.util.PRIMARY_SSID_PASSWORD_PARAM
import com.example.usbserialization.data.local.util.SUBNET_MASK_PARAM
import com.example.usbserialization.domain.model.NetworkSettings
import dagger.hilt.android.AndroidEntryPoint

/**
 * @author madhu.kumar
 */

private const val REQUEST_CODE_DEVICE_ADMIN = 1001

@AndroidEntryPoint
class WebViewActivity : AppCompatActivity() {
    private val networkViewModel: NetworkViewModel by viewModels()
    private lateinit var deviceAdminLauncher: ActivityResultLauncher<Intent>
    private lateinit var networkBroadcastReceiver: NetworkBroadcastReceiver
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleIntentData(intent)

        deviceAdminLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    startLockTaskMode()
                    setLockTaskPackagesAndStart()
                }
            }
        locationPermissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                when {
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                        registerNetworkComponents()
                    }
                    else -> {
                        Log.d("PermissionResult", "Location permissions denied")
                    }
                }
            }

        checkAndRequestLocationPermission()
        checkDeviceAdminAndStartLockTaskMode()
    }

    private fun registerNetworkComponents() {
        networkBroadcastReceiver =
            NetworkBroadcastReceiver { dhcpEnabled, ipAddress, gateway, dns1, dns2, subnetMask, ssidList ->
                networkViewModel.fetchNetworkSettings(dhcpEnabled, ipAddress, gateway, dns1, dns2, subnetMask, ssidList)
            }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            networkBroadcastReceiver,
            IntentFilter(NETWORK_INFO_UPDATED),
        )

        networkViewModel.registerNetworkCallback()
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("NetworkStateReceiver", "Location permission already granted")
            registerNetworkComponents()
        } else {
            requestLocationPermissions()
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    fun openWebBrowser(view: View) {
        networkViewModel.startWebServer()
        launchForm(networkViewModel.networkSettings.value)
    }

    private fun checkDeviceAdminAndStartLockTaskMode() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent) && devicePolicyManager.isDeviceOwnerApp(packageName)) {
            setLockTaskPackagesAndStart()
        } else {
            val intent =
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable this app as a device admin.")
                }
            deviceAdminLauncher.launch(intent)
        }
    }

    private fun setLockTaskPackagesAndStart() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        devicePolicyManager.setLockTaskPackages(
            adminComponent,
            arrayOf(
                "com.android.chrome", // Google Chrome
                "org.mozilla.firefox", // Mozilla Firefox
                "com.microsoft.emmx", // Microsoft Edge
                "com.sec.android.app.sbrowser", // Samsung Internet
                "com.opera.browser", // Opera
                "com.brave.browser", // Brave
                "com.duckduckgo.mobile.android", // DuckDuckGo Browser
                "com.vivaldi.browser", // Vivaldi
                "com.cloudmosa.puffinFree", // Puffin Browser
                "com.kiwibrowser.browser", // Kiwi Browser
                "com.android.systemui", // System UI (for handling navigation bars, status bar, etc.)
                "com.android.settings", // Settings app (for managing network connections, dialogs)
                "com.android.packageinstaller",
                packageName, // Whitelist your app
            ),
        )
        // Start the Kiosk Mode (lock task)
        startLockTaskMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentData(intent)
    }

    fun launchForm(settings: NetworkSettings) {
        val url = networkViewModel.getFormUrl(settings = settings)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun startLockTaskMode() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isInLockTaskMode = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

        if (!isInLockTaskMode) {
            startLockTask()
            Toast.makeText(this, "Lock Task Mode Enabled", Toast.LENGTH_SHORT).show()
        }
    }

    // Disable Lock Task Mode when exiting the activity
    private fun stopLockTaskMode() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isInLockTaskMode = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

        if (isInLockTaskMode) {
            stopLockTask()
            Toast.makeText(this, "Lock Task Mode Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkViewModel.stopWebServer()
        stopLockTaskMode()
        networkViewModel.unregisterNetworkCallback()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(networkBroadcastReceiver)
    }

    private fun handleIntentData(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            val dhcpEnabled = data.getQueryParameter(DHCP_ENABLED_PARAM)?.toBoolean() ?: false
            val ipAddress = data.getQueryParameter(IP_ADDRESS_PARAM) ?: ""
            val subnetMask = data.getQueryParameter(SUBNET_MASK_PARAM) ?: ""
            val gateway = data.getQueryParameter(GATEWAY_PARAM) ?: ""
            val dns1 = data.getQueryParameter(DNS1_PARAM) ?: ""
            val dns2 = data.getQueryParameter(DNS2_PARAM) ?: ""
            val primarySsid = data.getQueryParameter(PRIMARY_SSID_PARAM) ?: ""
            val fallbackSsid = data.getQueryParameter(FALLBACK_SSID_PARAM) ?: ""
            val primarySsidPassword = data.getQueryParameter(PRIMARY_SSID_PASSWORD_PARAM) ?: ""
            val fallbackSsidPassword = data.getQueryParameter(FALLBACK_SSID_PASSWORD_PARAM) ?: ""

            val networkSettings =
                NetworkSettings(
                    dhcpEnabled = dhcpEnabled,
                    ipAddress = ipAddress,
                    subnetMask = subnetMask,
                    gateway = gateway,
                    dns1 = dns1,
                    dns2 = dns2,
                    selectedPrimarySsid = primarySsid,
                    selectedSecondarySsid = fallbackSsid,
                    primarySsidPassword = primarySsidPassword,
                    secondarySsidPassword = fallbackSsidPassword,
                )

            networkViewModel.saveFormData(networkSettings)
            networkViewModel.stopWebServer()
        }
    }
}
