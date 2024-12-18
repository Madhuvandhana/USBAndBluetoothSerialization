package com.example.usbserialization.data

import com.example.usbserialization.data.remote.LocalWebServer
import com.example.usbserialization.di.IoDispatcher
import com.example.usbserialization.domain.WebServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author madhu.kumar
 */

@Singleton
class WebServerRepositoryImpl
    @Inject
    constructor(
        private val localWebServer: LocalWebServer,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WebServerRepository {
        @Throws(IOException::class)
        override suspend fun startWebServer() =
            withContext(ioDispatcher) {
                localWebServer.start()
            }

        @Throws(IOException::class)
        override suspend fun stopWebServer() =
            withContext(ioDispatcher) {
                localWebServer.stop()
            }
    }
