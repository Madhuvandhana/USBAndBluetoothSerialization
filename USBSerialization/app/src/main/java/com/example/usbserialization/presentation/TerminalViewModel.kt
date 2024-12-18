package com.example.usbserialization.presentation

import android.os.IBinder
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.viewModelScope
import com.example.usbserialization.data.SerialService
import com.example.usbserialization.domain.SerialRepository
import com.example.usbserialization.presentation.common.BaseViewModel
import com.example.usbserialization.presentation.utils.flowFromSuspend
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialPort
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kai_morich.simple_usb_terminal.SerialListener
import com.example.usbserialization.presentation.utils.TextUtil
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.ArrayDeque
import javax.inject.Inject

/**
 * @author madhu.kumar
 * Created 12/22/23 at 9:13 AM
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val serialRepository: SerialRepository,
) : BaseViewModel<ViewIntent, TerminalViewState, SingleEvent>(),
    DefaultLifecycleObserver {
    override val viewState: StateFlow<TerminalViewState>
    private var pendingNewline = false
    private var chatList = mutableStateListOf<ChatData>()

    private val exceptionHandler = CoroutineExceptionHandler {
            _, throwable ->
        Log.d("Exception in" + TerminalViewModel::class.java.simpleName, throwable.message.toString())
    }

    init {
        val initialVS = TerminalViewState.initial()
        viewState = merge(
            intentSharedFlow.filterIsInstance<ViewIntent.Initial>().take(1),
            intentSharedFlow.filterNot { it is ViewIntent.Initial },
        )
            .shareWhileSubscribed()
            .toPartialStateChangeFlow()
            .sendSingleEvent()
            .scan(initialVS) { vs, change -> change.reduce(vs) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                initialVS,
            )
    }

    private fun Flow<PartialStateChange>.sendSingleEvent(): Flow<PartialStateChange> {
        return onEach { change ->
            val event = when (change) {
                is PartialStateChange.USBCommunication.Send -> SingleEvent.OnSendSuccess
                is PartialStateChange.USBCommunication.OnConnectionChanged -> SingleEvent.OnConnectionChangedSuccess
                is PartialStateChange.USBCommunication.Receive -> SingleEvent.OnReceiveSuccess
                is PartialStateChange.USBCommunication.OnStatusChanged -> SingleEvent.OnStatusChangedSuccess
            }
            sendEvent(event)
        }
    }

    fun connect(portNum: Int, baudRate: Int, deviceId: Int, usbSerialPort: UsbSerialPort? = null) {
        viewModelScope.launch(exceptionHandler) {
            sendEvent(ViewIntent.ConnectionChanged(Connected.True))
            serialRepository.connect(portNum, baudRate, deviceId, usbSerialPort) {
                status(it)
            }
        }
    }

    fun connectToBluetooth(deviceAddress: String) {
        viewModelScope.launch(exceptionHandler) {
            sendEvent(ViewIntent.ConnectionChanged(Connected.True))
            serialRepository.connectBluetooth(deviceAddress) {
                status(it)
            }
        }
    }

    fun disconnect() {
        sendEvent(ViewIntent.ConnectionChanged(Connected.False))
        viewModelScope.launch(exceptionHandler) {
            serialRepository.disconnect()
        }
    }

    @Throws(IOException::class)
    suspend fun write(data: ByteArray?) {
        serialRepository.write(data)
    }

    suspend fun writeToBluetooth(data: ByteArray?) {
        serialRepository.writeToBluetooth(data)
    }

    fun attachListener(serialListener: SerialListener) {
        serialRepository.attachListener(serialListener)
    }

    fun detachListener() {
        serialRepository.detachListener()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun SharedFlow<ViewIntent>.toPartialStateChangeFlow(): Flow<PartialStateChange> {
        val receiveFlow = filterIsInstance<ViewIntent.Receive>()
            .distinctUntilChanged()
            .shareWhileSubscribed()

        val connectionChangedFlow = filterIsInstance<ViewIntent.ConnectionChanged>()
            .distinctUntilChanged()
            .shareWhileSubscribed()

        val statusChangedFlow = filterIsInstance<ViewIntent.StatusChanged>()
            .distinctUntilChanged()
            .shareWhileSubscribed()

        val sendFlow = filterIsInstance<ViewIntent.Send>()
            .distinctUntilChanged()
            .shareWhileSubscribed()

        val sendViaBluetoothFlow = filterIsInstance<ViewIntent.SendViaBluetooth>()
            .distinctUntilChanged()
            .shareWhileSubscribed()

        return merge(
            sendFlow.flatMapLatest {
                send(it.value, false)
            },
            sendViaBluetoothFlow.flatMapLatest {
                send(it.value, true)
            },
            receiveFlow.flatMapLatest {
                receive(it.datas)
            },
            connectionChangedFlow.map {
                PartialStateChange.USBCommunication.OnConnectionChanged(
                    connected = it.connected,
                )
            },

            statusChangedFlow.map {
                PartialStateChange.USBCommunication.OnStatusChanged(
                    value = it.status,
                )
            },

        )
    }

    private fun send(str: String, isBluetooth: Boolean) = flowFromSuspend {
        viewModelScope.launch(exceptionHandler) {
            if (viewState.value.connected != Connected.True) {
                return@launch
            }
            try {
                val data: ByteArray = (str + TextUtil.carriageReturn).toByteArray()
                if(isBluetooth) {
                    writeToBluetooth(data)
                } else {
                    write(data)
                }
            } catch (e: SerialTimeoutException) {
                status("write timeout: " + e.message)
            } catch (e: Exception) {
                onSerialError(e, "connection lost: ")
            }
        }
    }.map {
        chatList.add(ChatData(str, true))
        PartialStateChange.USBCommunication.Send(chatList)
    }

    /**
     * Asynchronous data arrival in serial ports may lead to varied timing between characters.
     * USB querying retrieves accumulated data from the converter chip, unaware of ongoing transmissions.
     * To ensure complete "messages," continuously read and concatenate results until the end of the message is reached.
     * For eg: a newline character is marked as end of message in this implementation.
     * Note the maximum data size for one USB read in this mode is relatively small.
     */

    private val spn = SpannableStringBuilder()
    private fun receive(datas: ArrayDeque<ByteArray?>?) = flowFromSuspend {
        viewModelScope.launch(exceptionHandler) {
            if (datas != null) {
                for (data in datas) {
                    val msg = data?.toString(Charsets.UTF_8) ?: ""
                    spn.append(msg)
                }
                val chatMessage = spn.toString()
                pendingNewline = chatMessage[chatMessage.length - 1] != '\r'
                if (pendingNewline) {
                    chatList.add(ChatData(spn.toString(), false))
                    spn.clear()
                }
            }
        }
    }.map {
        PartialStateChange.USBCommunication.Receive(chatList)
    }

    internal fun status(str: String) {
        sendEvent(ViewIntent.StatusChanged(str))
    }

    fun onSerialError(e: Exception, msg: String) {
        viewModelScope.launch(exceptionHandler) {
            status(msg + e.message)
            disconnect()
        }
    }

    fun isConnected() = serialRepository.isConnected()

    fun getService(): SerialService? = serialRepository.getService()

    fun register() {
        serialRepository.register()
    }

    fun unregister() {
        serialRepository.unregister()
    }

    fun init(binder: IBinder) {
        serialRepository.init(binder)
    }

    fun disconnectService() {
        serialRepository.disconnectService()
    }

    fun setConnection(connected: Connected) {
        serialRepository.setConnection(connected)
        sendEvent(ViewIntent.ConnectionChanged(connected))
    }

    fun sendEvent(viewIntent: ViewIntent) {
        viewModelScope.launch(exceptionHandler) {
            processIntent(viewIntent)
        }
    }
}
