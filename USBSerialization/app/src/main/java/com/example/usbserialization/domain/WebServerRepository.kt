package com.example.usbserialization.domain

/**
 * @author madhu.kumar
 * Created 9/30/24 at 1:05 PM
 */

interface WebServerRepository {
    suspend fun startWebServer()
    suspend fun stopWebServer()
}
