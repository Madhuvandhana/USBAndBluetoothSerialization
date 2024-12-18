package com.example.usbserialization.data.local.util

/**
 * @author madhu.kumar
 */

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences
    @Inject
    constructor(
        context: Context,
    ) : SecurePreferencesInterface {
        var masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        var sharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        override fun savePassword(
            key: String,
            password: String,
        ) {
            sharedPreferences.edit().putString(key, password).apply()
        }

        override fun getPassword(key: String): String? = sharedPreferences.getString(key, null)
    }
