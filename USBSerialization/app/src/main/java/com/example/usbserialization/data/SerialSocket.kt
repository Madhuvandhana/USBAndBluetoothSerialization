package com.example.usbserialization.data

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.usbserialization.presentation.Constants
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import de.kai_morich.simple_usb_terminal.SerialListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.InvalidParameterException
import java.util.UUID

private const val WRITE_WAIT_MILLIS = 2000 // 0 blocked infinitely on unprogrammed arduino
private val TAG = SerialSocket::class.java.simpleName
private const val BLUETOOTH_DATA_BUFFER_SIZE = 1024

internal class SerialSocket : SerialInputOutputManager.Listener {

    private var disconnectBroadcastReceiver: BroadcastReceiver
    private var context: Context
    private var listener: SerialListener? = null
    private var connection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var connected = false
    private val BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    constructor(context: Context, connection: UsbDeviceConnection?, serialPort: UsbSerialPort?) {
        if (context is Activity) throw InvalidParameterException("expected non UI context")
        this.context = context
        this.connection = connection
        this.serialPort = serialPort
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (listener != null) listener?.onSerialIoError(IOException("background disconnect"))
                disconnect() // disconnect now, else would be queued until UI re-attached
            }
        }
    }
    constructor(
        context: Context,
        device: BluetoothDevice?,
    ) {
        if (context is Activity) throw InvalidParameterException("expected non UI context")
        this.context = context
        this.bluetoothDevice = device
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (listener != null) listener?.onSerialIoError(IOException("background disconnect"))
                disconnectBluetooth() // disconnect now, else would be queued until UI re-attached
            }
        }
    }

    val name: String?
        get() = serialPort?.driver?.javaClass?.simpleName?.replace("SerialDriver", "")

    @Throws(IOException::class)
    internal fun connect(listener: SerialListener) {
        this.listener = listener
        ContextCompat.registerReceiver(
            context,
            disconnectBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_DISCONNECT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            serialPort?.dtr = true // for arduino, ...
            serialPort?.rts = true
        } catch (e: UnsupportedOperationException) {
            Log.d(TAG, "Failed to set initial DTR/RTS", e)
        }
        ioManager = SerialInputOutputManager(serialPort, this)
        ioManager?.start()
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    fun connectToBluetooth(listener: SerialListener) {
        this.listener = listener
        val intentFilter = IntentFilter(Constants.INTENT_ACTION_DISCONNECT)
        ContextCompat.registerReceiver(
            context,
            disconnectBroadcastReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            run()
        }
    }

    // Handled the permission at root level
    @SuppressLint("MissingPermission")
    private fun run() {
        try {
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            bluetoothSocket?.connect()
            listener?.onSerialConnect()
        } catch (e: Exception) {
            listener?.onSerialConnectError(e)
            try {
                bluetoothSocket?.close()
            } catch (ignored: Exception) {
            }
            bluetoothSocket = null
            return
        }
        connected = true
        try {
            val buffer = ByteArray(BLUETOOTH_DATA_BUFFER_SIZE)
            var len: Int?
            while (true) {
                len = bluetoothSocket?.inputStream?.read(buffer)
                val data = buffer.copyOf(len ?: 0)
                listener?.onSerialRead(data)
            }
        } catch (e: Exception) {
            connected = false
            listener?.onSerialIoError(e)
            try {
                bluetoothSocket?.close()
            } catch (ignored: Exception) {
            }
            bluetoothSocket = null
        }
    }

    internal fun disconnect() {
        listener = null // ignore remaining data and errors
        if (ioManager != null) {
            ioManager?.listener = null
            ioManager?.stop()
            ioManager = null
        }
        if (serialPort != null) {
            try {
                serialPort?.dtr = false
                serialPort?.rts = false
            } catch (ignored: Exception) {
            }
            try {
                serialPort?.close()
            } catch (ignored: Exception) {
            }
            serialPort = null
        }
        if (connection != null) {
            connection?.close()
            connection = null
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    internal fun disconnectBluetooth() {
        listener = null // ignore remaining data and errors
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket?.close()
            } catch (ignored: Exception) {
            }
            bluetoothSocket = null
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    internal fun getBluetoothSocket(): BluetoothSocket? = bluetoothSocket

    internal fun getUSBSerialPort(): UsbSerialPort? = serialPort

    @Throws(IOException::class)
    internal fun write(data: ByteArray?) {
        if (serialPort == null) throw IOException("not connected")
        serialPort?.write(data, WRITE_WAIT_MILLIS)
    }

    @Throws(IOException::class)
    internal fun writeToBluetooth(data: ByteArray?) {
        if (!connected) throw IOException("not connected")
        bluetoothSocket?.outputStream?.write(data)
    }

    override fun onNewData(data: ByteArray) {
        if (listener != null) listener?.onSerialRead(data)
    }

    override fun onRunError(e: Exception) {
        if (listener != null) listener?.onSerialIoError(e)
    }
}
