package com.example.usbserialization.domain

import android.os.IBinder
import com.example.usbserialization.data.SerialService
import com.example.usbserialization.presentation.Connected
import com.hoho.android.usbserial.driver.UsbSerialPort
import de.kai_morich.simple_usb_terminal.SerialListener

/**
 * @author madhu.kumar
 */
interface SerialRepository {
    suspend fun connect(
        portNum: Int,
        baudRate: Int,
        deviceId: Int,
        usbSerialPort: UsbSerialPort? = null,
        onStatusChanged: (value: String) -> Unit,
    )

    suspend fun connectBluetooth(deviceAddress: String, onStatusChanged: (value: String) -> Unit)
    suspend fun disconnect()

    suspend fun write(data: ByteArray?)

    suspend fun writeToBluetooth(data: ByteArray?)
    fun attachListener(listener: SerialListener)
    fun detachListener()
    fun init(binder: IBinder)
    fun getService(): SerialService?
    fun isConnected(): Boolean
    fun register()
    fun unregister()
    fun disconnectService()
    fun setConnection(connected: Connected)
}
