package com.example.usbserialization.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey
/**
 * @author madhu.kumar
 */

@Entity(tableName = "form_data")
data class FormData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ipAddress: String,
    val dhcpEnabled: Boolean,
    val subnetMask: String,
    val gateway: String,
    val dns1: String,
    val dns2: String,
    val primarySsid: String?,
    val secondarySsid: String?,
    val primarySsidPassword: String?,
    val secondarySsidPassword: String?
)

