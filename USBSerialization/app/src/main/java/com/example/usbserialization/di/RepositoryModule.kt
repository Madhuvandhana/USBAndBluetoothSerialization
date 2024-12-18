package com.example.usbserialization.di

import com.example.usbserialization.data.FormDataRepositoryImpl
import com.example.usbserialization.data.SerialRepositoryImpl
import com.example.usbserialization.data.WebServerRepositoryImpl
import com.example.usbserialization.data.local.util.NetworkStateReceiver
import com.example.usbserialization.domain.FormDataRepository
import com.example.usbserialization.domain.NetworkCallbackHandler
import com.example.usbserialization.domain.SerialRepository
import com.example.usbserialization.domain.WebServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * @author madhu.kumar
 * Created 9/30/24 at 1:08 PM
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSerialRepository(serialRepositoryImpl: SerialRepositoryImpl): SerialRepository

    @Binds
    @Singleton
    abstract fun bindFormDataRepository(formDataRepositoryImpl: FormDataRepositoryImpl): FormDataRepository

    @Binds
    @Singleton
    abstract fun bindWebServerRepository(webServerRepositoryImpl: WebServerRepositoryImpl): WebServerRepository

    @Binds
    @Singleton
    abstract fun bindNetworkStateReceiver(receiver: NetworkStateReceiver): NetworkCallbackHandler
}
