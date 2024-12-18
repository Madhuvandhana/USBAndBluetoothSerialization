package com.example.usbserialization.presentation

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.example.usbserialization.R
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.Locale

class DevicesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DevicesScreen(onItemClick = { item, baudRate ->
                        val args = Bundle()
                        args.putInt("device", item.device.deviceId)
                        args.putInt("port", item.port)
                        args.putInt("baud", baudRate)
                        val fragment: Fragment = TerminalFragment()
                        fragment.arguments = args
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, fragment, "terminal").addToBackStack(null).commit()
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onItemClick: (ListItem, Int) -> Unit) {
    var baudRate by remember { mutableStateOf(115200) }
    var listItems by remember { mutableStateOf(emptyList<ListItem>()) }

    var showDialog by remember { mutableStateOf(false) }

    val usbManager = LocalContext.current.getSystemService(Context.USB_SERVICE) as UsbManager
    val usbDefaultProber = UsbSerialProber.getDefaultProber()
    val usbCustomProber = CustomProber.customProber

    DisposableEffect(Unit) {
        refreshList(usbManager, usbDefaultProber, usbCustomProber, baudRate) { updatedList ->
            listItems = updatedList
        }
        onDispose { }
    }

    Column {
        TopAppBar(
            title = { Text(text = "USB Devices") },
            actions = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                }
                IconButton(onClick = {
                    refreshList(usbManager, usbDefaultProber, usbCustomProber, baudRate) { updatedList ->
                        listItems = updatedList
                    }
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                }
            },
        )
        val context = LocalContext.current
        DevicesList(listItems) { item ->
            if (item.driver == null) {
                Toast.makeText(context, "No driver", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Driver found", Toast.LENGTH_SHORT).show()
                onItemClick.invoke(item, baudRate)
            }
        }
        if (showDialog) {
            BaudRateDialog(
                baudRate = baudRate,
                onBaudRateSelected = { selectedRate ->
                    baudRate = selectedRate
                    showDialog = false
                    refreshList(usbManager, usbDefaultProber, usbCustomProber, baudRate) { updatedList ->
                        listItems = updatedList
                    }
                },
                onDismissRequest = { showDialog = false },
            )
        }
    }
}

@Composable
fun DevicesList(listItems: List<ListItem>, onItemClick: (ListItem) -> Unit) {
    LazyColumn {
        items(listItems) { item ->
            ListItem(
                item = item,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
fun ListItem(item: ListItem, onItemClick: (ListItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onItemClick(item) },
    ) {
        Text(
            text = if (item.driver == null) {
                "<no driver>"
            } else {
                if (item.driver.ports.size == 1) {
                    item.driver.javaClass.simpleName.replace("SerialDriver", "")
                } else {
                    item.driver.javaClass.simpleName.replace("SerialDriver", "") +
                        ", Port " + item.port
                }
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = String.format(Locale.US, "Vendor %04X, Product %04X", item.device.vendorId, item.device.productId),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
        )
    }
}

@Composable
fun BaudRateDialog(
    baudRate: Int,
    onBaudRateSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val baudRates = stringArrayResource(R.array.baud_rates)

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text("Baud rate") },
        confirmButton = {
            for (i in baudRates.indices) {
                DialogButton(
                    text = baudRates[i],
                    enabled = baudRate.toString() == baudRates[i],
                    onClick = {
                        onBaudRateSelected(baudRates[i].toInt())
                        onDismissRequest()
                    },
                )
            }
        },
    )
}

@Composable
fun DialogButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

data class ListItem(
    val device: UsbDevice,
    val port: Int,
    val driver: UsbSerialDriver?,
)

fun refreshList(
    usbManager: UsbManager,
    usbDefaultProber: UsbSerialProber,
    usbCustomProber: UsbSerialProber,
    baudRate: Int,
    onListUpdated: (List<ListItem>) -> Unit,
) {
    val updatedList = mutableListOf<ListItem>()

    for (device in usbManager.deviceList.values) {
        var driver = usbDefaultProber.probeDevice(device)

        if (driver == null) {
            driver = usbCustomProber.probeDevice(device)
        }

        if (driver != null) {
            for (port in 0 until driver.ports.size) {
                updatedList.add(ListItem(device, port, driver))
            }
        } else {
            updatedList.add(ListItem(device, 0, null))
        }
    }

    onListUpdated(updatedList)
}
