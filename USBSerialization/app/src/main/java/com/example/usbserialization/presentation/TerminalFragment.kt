package com.example.usbserialization.presentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.usbserialization.data.SerialService
import de.kai_morich.simple_usb_terminal.SerialListener
import java.util.ArrayDeque

/**
 * @author madhu.kumar
 * Created 12/20/23 at 9:39 AM
 */

class TerminalFragment : Fragment(), ServiceConnection, SerialListener {

    val viewModel: TerminalViewModel by activityViewModels()

    private var deviceId = 0
    private var portNum: Int = 0
    private var baudRate: Int = 0
    private var initialStart = true
    private var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = arguments?.getInt("device") ?: 0
        portNum = arguments?.getInt("port") ?: 0
        baudRate = arguments?.getInt("baud") ?: 0
        deviceAddress = arguments?.getString("address")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                USBCommunicationScreen(viewModel = viewModel, isBluetooth = deviceAddress!= null)
            }
        }
    }

    override fun onServiceConnected(p0: ComponentName?, binder: IBinder) {
        viewModel.init(binder)
        viewModel.attachListener(this)
        if (initialStart && isResumed) {
            initialStart = false
            if (deviceId != 0) {
                viewModel.connect(portNum, baudRate, deviceId)
            }
            if (deviceAddress != null) {
                viewModel.connectToBluetooth(deviceAddress.toString())
            }
        }
    }

    override fun onServiceDisconnected(p0: ComponentName?) = viewModel.disconnectService()

    override fun onSerialConnect() {
        viewModel.status("connected")
        viewModel.setConnection(Connected.True)
    }

    override fun onSerialConnectError(e: Exception?) {
        viewModel.status("connection failed: " + e?.message)
        viewModel.disconnect()
    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray?>()
        datas.add(data)
        viewModel.sendEvent(ViewIntent.Receive(datas))
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        viewModel.sendEvent(ViewIntent.Receive(datas))
    }

    override fun onSerialIoError(e: Exception?) {
        viewModel.status("connection lost: " + e?.message)
        viewModel.disconnect()
    }

    override fun onDestroy() {
        if (viewModel.isConnected()) viewModel.disconnect()
        context?.stopService(Intent(context, SerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.getService() != null) {
            viewModel.attachListener(this)
        } else {
            requireActivity().startService(
                Intent(
                    context,
                    SerialService::class.java,
                ),
            ) // prevents service destroy on unbind from recreated activity caused by orientation change
        }
    }

    override fun onStop() {
        if (viewModel.getService() != null && !requireActivity().isChangingConfigurations) {
            viewModel.detachListener()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.register()
        if (initialStart && viewModel.getService() != null) {
            initialStart = false
            if (deviceId != 0) {
                viewModel.connect(portNum, baudRate, deviceId)
            }
            if (deviceAddress != null) {
                viewModel.connectToBluetooth(deviceAddress.toString())
            }
        }
    }
    override fun onPause() {
        viewModel.unregister()
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().bindService(
            Intent(context, SerialService::class.java),
            this,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    fun status(str: String) {
        viewModel.sendEvent(ViewIntent.StatusChanged(str))
    }
}
