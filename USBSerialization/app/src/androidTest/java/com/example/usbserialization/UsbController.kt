package com.example.usbserialization

import java.io.IOException

/**
 * @author madhu.kumar
 * Created 1/9/24 at 6:50 PM
 */
class UsbController {
    @Throws(IOException::class)
    fun unbindUsbDevice(command: String) {
        Runtime.getRuntime().exec(command)
    }
}
