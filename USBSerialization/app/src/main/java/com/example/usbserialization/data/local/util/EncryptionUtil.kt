package com.example.usbserialization.data.local.util

import android.util.Base64
import com.example.usbserialization.BuildConfig
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * @author madhu.kumar
 */

 fun decryptPassword(encryptedPassword: String): String {
    val key = Base64.decode(BuildConfig.ENCRYPTION_KEY, Base64.DEFAULT)
    val iv = Base64.decode(BuildConfig.IV, Base64.DEFAULT)
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    val secretKeySpec = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

    val decodedBytes = Base64.decode(encryptedPassword, Base64.DEFAULT)
    val decryptedBytes = cipher.doFinal(decodedBytes)
    return String(decryptedBytes, Charsets.UTF_8)
}