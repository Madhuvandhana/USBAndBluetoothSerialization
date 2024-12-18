package com.example.usbserialization.presentation

/**
 * @author madhu.kumar
 * Created 4/3/24 at 2:00 PM
 */
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import com.example.usbserialization.R
import com.example.usbserialization.presentation.utils.BluetoothUtil

class BluetoothFragment : Fragment() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val listItems = mutableStateListOf<BluetoothDevice>()
    private var permissionMissing by mutableStateOf(false)

    private val requestBluetoothPermissionLauncherForRefresh = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            refresh()
        } else {
            Toast.makeText(requireActivity(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                BluetoothDevicesScreen()
            }
            if (activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) == true) {
                val bluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothAdapter = bluetoothManager.adapter
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BluetoothDevicesScreen() {
        LaunchedEffect(Unit) {
            refresh()
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Bluetooth Devices") },
                    actions = {
                        IconButton(onClick = {
                            if (BluetoothUtil.hasPermissions(
                                    this@BluetoothFragment,
                                    requestBluetoothPermissionLauncherForRefresh,
                                )
                            ) {
                                refresh()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                    },
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(listItems) { device ->
                            DeviceListItem(device = device)
                        }
                    }
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun DeviceListItem(device: BluetoothDevice) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .clickable(onClick = {
                    val args = Bundle()
                    args.putString("address", device.address)
                    val fragment: Fragment = TerminalFragment()
                    fragment.arguments = args
                    parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment, "terminal")
                        .addToBackStack(null)
                        .commit()
                }),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp,
            ),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = device.name ?: "Unknown device")
                Text(text = device.address)
            }
        }
    }

    // Permission handled at root level
    @SuppressLint("MissingPermission")
    fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionMissing = checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            }
            if (!permissionMissing) {
                bluetoothAdapter?.bondedDevices?.filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                    ?.let {
                        listItems.addAll(it)
                        listItems.sortWith(BluetoothUtil::compareTo)
                    }
            }
        }
        if (bluetoothAdapter == null) {
            Toast.makeText(requireActivity(), "<bluetooth not supported>", Toast.LENGTH_SHORT).show()
        } else if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(requireActivity(), "<bluetooth is disabled>", Toast.LENGTH_SHORT).show()
        } else if (permissionMissing) {
            Toast.makeText(requireActivity(), "<permission missing, use REFRESH>", Toast.LENGTH_SHORT).show()
        } else if (listItems.isEmpty()) {
            Toast.makeText(requireActivity(), "<no bluetooth devices found>", Toast.LENGTH_SHORT).show()
        }
    }
}
