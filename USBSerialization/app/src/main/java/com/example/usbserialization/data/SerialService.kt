package com.example.usbserialization.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.usbserialization.R
import com.example.usbserialization.presentation.Connected
import com.example.usbserialization.presentation.Constants
import com.example.usbserialization.presentation.CustomProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import de.kai_morich.simple_usb_terminal.SerialListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.ArrayDeque

/**
 * @see https://github.com/kai-morich/SimpleUsbTerminal
 * Background service to remain connected even when the app is in the background
 * or undergoing orientation change.
 */

class SerialService : Service(), SerialListener {

    internal inner class SerialBinder : Binder() {
        val service: SerialService
            get() = this@SerialService
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private enum class QueueType {
        Connect, ConnectError, Read, IoError
    }

    private class QueueItem {
        var type: QueueType
        var datas: ArrayDeque<ByteArray?>? = null
        var e: Exception? = null

        internal constructor(type: QueueType) {
            this.type = type
            if (type == QueueType.Read) init()
        }

        internal constructor(type: QueueType, e: Exception?) {
            this.type = type
            this.e = e
        }

        internal constructor(type: QueueType, datas: ArrayDeque<ByteArray?>?) {
            this.type = type
            this.datas = datas
        }

        fun init() {
            datas = ArrayDeque()
        }

        fun add(data: ByteArray?) {
            datas?.add(data)
        }
    }

    private val binder: IBinder by lazy { SerialBinder() }
    private val queue1: ArrayDeque<QueueItem> = ArrayDeque()
    private val queue2: ArrayDeque<QueueItem> = ArrayDeque()
    private val lastRead: QueueItem = QueueItem(QueueType.Read)
    private var socket: SerialSocket? = null
    private var listener: SerialListener? = null
    private var connected: Connected = Connected.False
    private var permissionGranted: Boolean? = null
    private var usbSerialPort: UsbSerialPort? = null

    private val USBBroadcastReceiver = USBBroadcastReceiver { granted ->
        permissionGranted = granted
    }

    override fun onDestroy() {
        cancelNotification()
        disconnect()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    @Throws(IOException::class)
    internal fun connect(
        context: Context,
        usbManager: UsbManager,
        portNum: Int,
        baudRate: Int,
        deviceId: Int,
        usbSerialPort: UsbSerialPort? = null,
        onStatusChanged: (value: String) -> Unit,
    ) {
        var device: UsbDevice? = null
        for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        if (device == null) {
            onStatusChanged("connection failed: device not found")
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.customProber.probeDevice(device)
        }
        if (driver == null) {
            onStatusChanged("connection failed: no driver for device")
            return
        }
        if (driver.ports.size < portNum) {
            onStatusChanged("connection failed: not enough ports at device")
            return
        }

        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.device)) {
            val flags = PendingIntent.FLAG_MUTABLE
            val usbPermissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(Constants.INTENT_ACTION_GRANT_USB),
                flags,
            )
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.device)) {
                onStatusChanged("connection failed: permission denied")
            } else {
                onStatusChanged("connection failed: open failed")
            }
            return
        }

        connected = Connected.Pending
        try {
            val actualUsbSerialPort = usbSerialPort ?: driver.ports[portNum] // Use the provided or default port
            actualUsbSerialPort.open(usbConnection)
            try {
                actualUsbSerialPort.setParameters(
                    baudRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE,
                )
            } catch (e: UnsupportedOperationException) {
                onStatusChanged("Setting serial parameters failed: " + e.message)
            }
            val socket = SerialSocket(
                context.applicationContext,
                usbConnection,
                actualUsbSerialPort,
            )

            socket.connect(this)
            this.socket = socket
            connected = Connected.True
            listener?.onSerialConnect()
        } catch (e: Exception) {
            listener?.onSerialConnectError(e)
        }
    }

    internal fun connectBluetooth(deviceAddress: String, context: Context, onStatusChanged: (value: String) -> Unit) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connected = Connected.Pending
            val socket = SerialSocket(context.applicationContext, device)
            socket.connectToBluetooth(this)
            this.socket = socket
            connected = Connected.True
            listener?.onSerialConnect()
            onStatusChanged.invoke("Successfully connected")
        } catch (e: Exception) {
            onStatusChanged.invoke("connection failed: " + e.message)
            listener?.onSerialConnectError(e)
        }
    }

    internal fun disconnect() {
        connected = Connected.False
        cancelNotification()
        if (socket != null) {
            if (socket?.getUSBSerialPort() != null) {
                socket?.disconnect()
            }
            if (socket?.getBluetoothSocket() != null) {
                socket?.disconnectBluetooth()
            }
            socket = null
        }
        usbSerialPort = null
    }

    @Throws(IOException::class)
    internal fun write(data: ByteArray?) {
        if (connected == Connected.False) throw IOException("not connected")
        socket?.write(data)
    }

    @Throws(IOException::class)
    internal fun writeToBluetooth(data: ByteArray?) {
        if (connected == Connected.False) throw IOException("not connected")
        socket?.writeToBluetooth(data)
    }

    internal fun attach(listener: SerialListener) {
        cancelNotification()
        synchronized(this) {
            this.listener = listener
        }
        processQueues(queue1)
        processQueues(queue2)
        queue1.clear()
        queue2.clear()
    }

    internal fun detach() {
        if (connected == Connected.True) createNotification()
        listener = null
    }

    private fun processQueues(queue: ArrayDeque<QueueItem>) {
        for (item in queue) {
            when (item.type) {
                QueueType.Connect -> listener?.onSerialConnect()
                QueueType.ConnectError -> {
                    listener?.onSerialConnectError(item.e)
                    disconnect()
                }
                QueueType.Read -> listener?.onSerialRead(item.datas)
                QueueType.IoError -> {
                    listener?.onSerialIoError(item.e)
                    disconnect()
                }
            }
        }
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nc = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL,
                "Background service",
                NotificationManager.IMPORTANCE_LOW,
            )
            nc.setShowBadge(false)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(nc)
        }
        val disconnectIntent = Intent()
            .setAction(Constants.INTENT_ACTION_DISCONNECT)
        val restartIntent = Intent()
            .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PendingIntent.FLAG_IMMUTABLE
        val disconnectPendingIntent =
            PendingIntent.getBroadcast(this, 1, disconnectIntent, flags)
        val restartPendingIntent =
            PendingIntent.getActivity(this, 1, restartIntent, flags)
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(resources.getColor(R.color.colorPrimary))
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(if (socket != null) "Connected to " + socket?.name else "Background Service")
            .setContentIntent(restartPendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_clear_white_24dp,
                    "Disconnect",
                    disconnectPendingIntent,
                ),
            )
        val notification = builder.build()
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)
    }

    private fun cancelNotification() {
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    override fun onSerialConnect() {
        if (connected == Connected.True) {
            synchronized(this) {
                if (listener != null) {
                    scope.launch {
                        listener?.onSerialConnect()
                    }
                } else {
                    queue2.add(QueueItem(QueueType.Connect))
                }
            }
        }
    }

    override fun onSerialConnectError(e: Exception?) {
        if (connected == Connected.True) {
            synchronized(this) {
                if (listener != null) {
                    scope.launch {
                        listener?.onSerialConnectError(e)
                    }
                } else {
                    queue2.add(QueueItem(QueueType.ConnectError, e))
                    disconnect()
                }
            }
        }
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        throw UnsupportedOperationException()
    }

    override fun onSerialRead(data: ByteArray?) {
        if (connected == Connected.True) {
            synchronized(this) {
                if (listener != null) {
                    var first: Boolean
                    synchronized(lastRead) {
                        first = lastRead.datas?.isEmpty() ?: true
                        lastRead.add(data)
                    }
                    if (first) {
                        scope.launch {
                            var datas: ArrayDeque<ByteArray?>?
                            synchronized(lastRead) {
                                datas = lastRead.datas
                                lastRead.init()
                            }
                            if (listener != null) {
                                listener?.onSerialRead(datas)
                            } else {
                                queue1.add(QueueItem(QueueType.Read, datas))
                            }
                        }
                    }
                } else {
                    if (queue2.isEmpty() || queue2.last.type != QueueType.Read) {
                        queue2.add(
                            QueueItem(
                                QueueType.Read,
                            ),
                        )
                    }
                    queue2.last.add(data)
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception?) {
        if (connected == Connected.True) {
            synchronized(this) {
                if (listener != null) {
                    scope.launch {
                        listener?.onSerialIoError(e)
                    }
                } else {
                    queue2.add(QueueItem(QueueType.IoError, e))
                    disconnect()
                }
            }
        }
    }

    internal fun isConnected(): Boolean = connected == Connected.True

    internal fun register(context: Context) {
        ContextCompat.registerReceiver(
            context,
            USBBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_GRANT_USB),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    internal fun unregister(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(USBBroadcastReceiver)
    }

    internal fun setConnection(connected: Connected) {
        this.connected = connected
    }
}
