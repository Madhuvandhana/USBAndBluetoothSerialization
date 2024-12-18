package com.example.usbserialization.data.remote
import android.content.Context
import android.util.Log
import com.example.usbserialization.BuildConfig
import com.example.usbserialization.data.local.util.DHCP_ENABLED_PARAM
import com.example.usbserialization.data.local.util.DNS1_PARAM
import com.example.usbserialization.data.local.util.DNS2_PARAM
import com.example.usbserialization.data.local.util.GATEWAY_PARAM
import com.example.usbserialization.data.local.util.IP_ADDRESS_PARAM
import com.example.usbserialization.data.local.util.PRIMARY_SSID_PARAM
import com.example.usbserialization.data.local.util.SUBNET_MASK_PARAM
import com.example.usbserialization.data.local.util.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.security.KeyStore
import javax.inject.Inject

private const val KEYSTORE_PASSWORD = "keystore_password"
private const val PRIVATEKEY_PASSWORD = "privatekey_password"
private const val DEFAULT_PASSWORD = "default_password"
private const val KTOR_KEYSTORE_FILE = "ktor-server.jks"
private const val KTOR_KEY_ALIAS = "ktorserver"
private const val GRACE_PERIOD_IN_MS = 1_000L
private const val TIMEOUT_IN_MS = 10_000L
private const val SSL_PORT = 8443
private const val HTTP_PORT = 8080
private const val UNKNOWN_PARAM = "Unknown"

/**
 * @author madhu.kumar
 */

class LocalWebServer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val securePreferences: SecurePreferences,
    ) {
        init {
            initializeServerPasswords()
        }

        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

        fun start() {
            if (server == null) {
                server = createServer()
                server?.start(wait = false)
                Log.d("LocalWebServer", "Server started.")
            } else {
                Log.d("LocalWebServer", "Server is already running.")
            }
        }

        fun stop() {
            server?.apply {
                stop(0, 0)
                Log.d("LocalWebServer", "Server stopped successfully")
                server = null
            } ?: run {
                Log.d("LocalWebServer", "Server is not running")
            }
        }

        private fun createServer() =
            embeddedServer(
                Netty,
                environment = applicationEnvironment { log = NOPLogger.NOP_LOGGER },
                configure = {
                    sslConnector(
                        keyStore = buildKeyStore(),
                        keyAlias = KTOR_KEY_ALIAS,
                        keyStorePassword = { getDecryptedKeyStorePassword().toCharArray() },
                        privateKeyPassword = { getDecryptedPrivateKeyPassword().toCharArray() },
                    ) {
                        port = SSL_PORT
                    }

                    connector {
                        port = HTTP_PORT
                    }
                },
            ) {
                routing {
                    configureRouting(context)
                }
            }

        private fun getDecryptedKeyStorePassword(): String = securePreferences.getPassword(KEYSTORE_PASSWORD) ?: DEFAULT_PASSWORD

        private fun getDecryptedPrivateKeyPassword(): String = securePreferences.getPassword(PRIVATEKEY_PASSWORD) ?: DEFAULT_PASSWORD

        private fun buildKeyStore(): KeyStore {
            val keyStoreFile = File(context.filesDir, KTOR_KEYSTORE_FILE)
            val keyStore =
                buildKeyStore {
                    certificate(KTOR_KEY_ALIAS) {
                        password = getDecryptedPrivateKeyPassword()
                        domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
                    }
                }
            keyStore.saveToFile(keyStoreFile, getDecryptedKeyStorePassword())
            return keyStore
        }

        private fun Application.configureRouting(context: Context) {
            routing {
                get("/") {
                    call.respondText("Hello, Ktor with SSL!")
                }

                get("/form.html") {
                    val assetManager = context.assets
                    try {
                        assetManager.open("form.html").use { inputStream ->
                            call.respondBytes(inputStream.readBytes(), contentType = io.ktor.http.ContentType.Text.Html)
                        }
                    } catch (e: Exception) {
                        Log.e("LocalWebServer", "Error loading form.html: ${e.message}")
                        call.respondText("404 - File Not Found", status = io.ktor.http.HttpStatusCode.NotFound)
                    }
                }

                get("/submit") {
                    val urlParams = call.request.queryParameters
                    val dhcpEnabled = urlParams[DHCP_ENABLED_PARAM]?.toBoolean() ?: false
                    val ipAddress = urlParams[IP_ADDRESS_PARAM] ?: UNKNOWN_PARAM
                    val subnetMask = urlParams[SUBNET_MASK_PARAM] ?: UNKNOWN_PARAM
                    val gateway = urlParams[GATEWAY_PARAM] ?: UNKNOWN_PARAM
                    val dns1 = urlParams[DNS1_PARAM] ?: UNKNOWN_PARAM
                    val dns2 = urlParams[DNS2_PARAM] ?: UNKNOWN_PARAM
                    val ssid = urlParams[PRIMARY_SSID_PARAM] ?: UNKNOWN_PARAM

                    call.respondText(
                        "Form submitted! DHCP Enabled: $dhcpEnabled, IP Address: $ipAddress," +
                            " Subnet Mask: $subnetMask, Gateway: $gateway, DNS 1: $dns1, DNS 2: $dns2, " +
                            "SSID:  $ssid",
                    )
                }

                route("{...}") {
                    handle {
                        call.respondText("404 - Page Not Found", status = io.ktor.http.HttpStatusCode.NotFound)
                    }
                }
            }
        }

        private fun initializeServerPasswords() {
            val keystorePassword = BuildConfig.KEYSTORE_PASSWORD
            val privateKeyPassword = BuildConfig.PRIVATEKEY_PASSWORD

            securePreferences.savePassword(KEYSTORE_PASSWORD, keystorePassword)
            securePreferences.savePassword(PRIVATEKEY_PASSWORD, privateKeyPassword)
        }
    }
