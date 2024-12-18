package com.example.usbserialization.presentation

import com.example.usbserialization.presentation.common.MviIntent
import com.example.usbserialization.presentation.common.MviSingleEvent
import com.example.usbserialization.presentation.common.MviViewState
import com.example.usbserialization.presentation.utils.TextUtil
import java.util.ArrayDeque
import javax.annotation.concurrent.Immutable

/**
 * @author madhu.kumar
 * Created 12/22/23 at 9:37 AM
 */
@Immutable
data class TerminalViewState(
    val connected: Connected,
    val deviceId: Int,
    val portNum: Int,
    val baudRate: Int,
    val initialStart: Boolean,
    val hexEnabled: Boolean,
    val controlLinesEnabled: Boolean,
    val pendingNewline: Boolean,
    val newline: String,
    val chatList: List<ChatData>,
    val status: String,
) : MviViewState {
    companion object {
        fun initial() = TerminalViewState(
            connected = Connected.False,
            deviceId = 0,
            portNum = 0,
            baudRate = 0,
            initialStart = true,
            hexEnabled = false,
            controlLinesEnabled = false,
            pendingNewline = false,
            newline = TextUtil.newline_crlf,
            chatList = emptyList(),
            status = "",
        )
    }
}

@Immutable
sealed interface ViewIntent : MviIntent {
    object Initial : ViewIntent
    data class Send(val value: String) : ViewIntent

    data class SendViaBluetooth(val value: String) : ViewIntent
    data class Receive(val datas: ArrayDeque<ByteArray?>?) : ViewIntent
    data class ConnectionChanged(val connected: Connected) : ViewIntent
    data class StatusChanged(val status: String) : ViewIntent
}

sealed interface SingleEvent : MviSingleEvent {
    object OnSendSuccess : SingleEvent
    object OnReceiveSuccess : SingleEvent
    object OnStatusChangedSuccess : SingleEvent
    object OnConnectionChangedSuccess : SingleEvent
}
internal sealed interface PartialStateChange {
    fun reduce(viewState: TerminalViewState): TerminalViewState
    sealed interface USBCommunication : PartialStateChange {
        override fun reduce(viewState: TerminalViewState): TerminalViewState {
            return when (this) {
                is Send -> {
                    viewState.copy(
                        chatList = value,
                    )
                }

                is OnConnectionChanged -> {
                    viewState.copy(connected = connected)
                }

                is Receive -> {
                    viewState.copy(
                        chatList = value,
                    )
                }

                is OnStatusChanged -> {
                    viewState.copy(
                        status = value,
                    )
                }
            }
        }

        data class Send(val value: List<ChatData>) : USBCommunication
        data class OnConnectionChanged(val connected: Connected) : USBCommunication
        data class Receive(val value: List<ChatData>) : USBCommunication
        data class OnStatusChanged(val value: String) : USBCommunication
    }
}
enum class Connected {
    False, Pending, True
}

data class ChatData(val value: String, val isSent: Boolean)
