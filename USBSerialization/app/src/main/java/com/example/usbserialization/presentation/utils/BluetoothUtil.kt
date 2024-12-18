package com.example.usbserialization.presentation.utils

/**
 * @author madhu.kumar
 * Created 3/26/24 at 10:47 PM
 */

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.example.usbserialization.BuildConfig
import com.example.usbserialization.R

object BluetoothUtil {

    interface PermissionGrantedCallback {
        fun call()
    }

    /**
     * sort by name, then address. sort named devices first
     */
    @SuppressLint("MissingPermission")
    fun compareTo(a: BluetoothDevice, b: BluetoothDevice): Int {
        val aValid = !a.name.isNullOrEmpty()
        val bValid = !b.name.isNullOrEmpty()
        if (aValid && bValid) {
            val ret = a.name.compareTo(b.name)
            if (ret != 0) return ret
            return a.address.compareTo(b.address)
        }
        if (aValid) return -1
        return if (bValid) 1 else a.address.compareTo(b.address)
    }

    /**
     * Android 12 permission handling
     */
    private fun showRationaleDialog(fragment: Fragment, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(fragment.requireActivity())
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title))
        builder.setMessage(fragment.getString(R.string.bluetooth_permission_grant))
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Continue", listener)
        builder.show()
    }

    private fun showSettingsDialog(fragment: Fragment) {
        val s = fragment.resources.getString(fragment.resources.getIdentifier("@android:string/permgrouplab_nearby_devices", null, null))
        val builder = AlertDialog.Builder(fragment.requireActivity())
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title))
        builder.setMessage(String.format(fragment.getString(R.string.bluetooth_permission_denied), s))
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Settings") { dialog, which ->
            fragment.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + BuildConfig.APPLICATION_ID)))
        }
        builder.show()
    }

    fun hasPermissions(fragment: Fragment, requestPermissionLauncher: ActivityResultLauncher<String>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val missingPermissions = fragment.requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        val showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        if (missingPermissions) {
            if (showRationale) {
                showRationaleDialog(fragment) { dialog, which ->
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return false
        } else {
            return true
        }
    }

    fun onPermissionsResult(fragment: Fragment, granted: Boolean, cb: PermissionGrantedCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        if (granted) {
            cb.call()
        } else if (showRationale) {
            showRationaleDialog(fragment) { dialog, which -> cb.call() }
        } else {
            showSettingsDialog(fragment)
        }
    }
}
