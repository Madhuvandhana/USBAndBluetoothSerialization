package com.example.usbserialization.domain

/**
 * @author madhu.kumar
 */
interface NetworkCallbackHandler {
    fun registerNetworkCallback()

    fun unregisterNetworkCallback()
}
