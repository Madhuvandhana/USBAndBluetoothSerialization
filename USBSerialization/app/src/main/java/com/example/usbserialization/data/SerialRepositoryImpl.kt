package com.example.usbserialization.data

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.IBinder
import com.example.usbserialization.domain.SerialRepository
import com.example.usbserialization.presentation.Connected
import com.hoho.android.usbserial.driver.UsbSerialPort
import de.kai_morich.simple_usb_terminal.SerialListener
import java.io.IOException
import javax.inject.Inject

class SerialRepositoryImpl @Inject constructor(private val usbManager: UsbManager, private val context: Context) :
    SerialRepository {

    private var serialService: SerialService? = null

    @Throws(IOException::class)
    override suspend fun connect(
        portNum: Int,
        baudRate: Int,
        deviceId: Int,
        usbSerialPort: UsbSerialPort?,
        onStatusChanged: (value: String) -> Unit,
    ) {
        serialService?.connect(context, usbManager, portNum, baudRate, deviceId, usbSerialPort, onStatusChanged)
    }

    override suspend fun connectBluetooth(deviceAddress: String, onStatusChanged: (value: String) -> Unit) {
        serialService?.connectBluetooth(deviceAddress, context, onStatusChanged)
    }

    override suspend fun disconnect() {
        serialService?.disconnect()
    }

    @Throws(IOException::class)
    override suspend fun write(data: ByteArray?) {
        serialService?.write(data)
    }

    override suspend fun writeToBluetooth(data: ByteArray?) {
        serialService?.writeToBluetooth(data)
    }

    override fun attachListener(listener: SerialListener) {
        serialService?.attach(listener)
    }

    override fun detachListener() {
        serialService?.detach()
    }

    override fun init(binder: IBinder) {
        serialService = (binder as SerialService.SerialBinder).service
    }

    override fun getService(): SerialService? = serialService

    override fun isConnected(): Boolean = serialService?.isConnected() ?: false

    override fun register() {
        serialService?.register(context)
    }

    override fun unregister() {
        serialService?.unregister(context)
    }

    override fun disconnectService() {
        serialService = null
    }

    override fun setConnection(connected: Connected) {
        serialService?.setConnection(connected = connected)
    }
}
