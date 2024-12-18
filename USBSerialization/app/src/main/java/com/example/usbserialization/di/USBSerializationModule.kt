package com.example.usbserialization.di

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.room.Room
import com.example.usbserialization.data.local.AppDatabase
import com.example.usbserialization.data.local.FormDataDao
import com.example.usbserialization.data.local.util.SecurePreferences
import com.example.usbserialization.data.remote.LocalWebServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * @author madhu.kumar
 * Created 12/19/23 at 3:25 PM
 */
@Module
@InstallIn(SingletonComponent::class)
object USBSerializationModule {
    @Provides
    fun provideContext(
        @ApplicationContext context: Context,
    ): Context {
        return context
    }

    @Provides
    fun provideUsbManager(@ApplicationContext context: Context): UsbManager {
        return context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    @Singleton
    @Provides
    fun providesIOCoroutineScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private const val DB_NAME = "appBluetooth.db"

    @Provides
    @Singleton
    fun provideFormDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            DB_NAME,
        ).build()
    }

    @Provides
    @Singleton
    fun provideFormDataDao(database: AppDatabase): FormDataDao {
        return database.formDataDao()
    }

    @Provides
    fun provideSecurePreferences(
        @ApplicationContext context: Context,
    ): SecurePreferences {
        return SecurePreferences(context)
    }

    @Provides
    @Singleton
    fun provideLocalWebServer(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences,
    ): LocalWebServer {
        return LocalWebServer(context, securePreferences)
    }
}
