package com.example.usbserialization.data.local.util

/**
 * @author madhu.kumar
 */

interface SecurePreferencesInterface {
    fun savePassword(key: String, password: String)
    fun getPassword(key: String): String?
}
