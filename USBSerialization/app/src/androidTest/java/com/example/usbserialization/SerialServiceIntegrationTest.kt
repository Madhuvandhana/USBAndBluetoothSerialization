package com.example.usbserialization

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants.USB_SUBCLASS_VENDOR_SPEC
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ServiceTestRule
import app.cash.turbine.test
import com.example.usbserialization.data.SerialRepositoryImpl
import com.example.usbserialization.data.SerialService
import com.example.usbserialization.domain.SerialRepository
import com.example.usbserialization.presentation.Connected
import com.example.usbserialization.presentation.TerminalViewModel
import com.hoho.android.usbserial.driver.UsbSerialPort
import de.kai_morich.simple_usb_terminal.SerialListener
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque

/**
 * @author madhu.kumar
 * Created 1/5/24 at 3:11 PM
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@LargeTest
class SerialServiceIntegrationTest : SerialListener {
    @get:Rule
    val serviceRule = ServiceTestRule()
    lateinit var context: Context
    var usbManager = mockk<UsbManager>(relaxed = true)
    private val deviceId = 1045
    private val deviceDriverName = "Cp21xxSerialDriver"
    private val usbInterfaceName = "CP2102 USB to UART Bridge Controller"
    private val vendorId = 0x10C4
    private val productId = 0xEA60
    private val manufactureName = "Silicon Labs"
    private val version = "1.00"
    private val serialNumber = "0001"
    private val deviceName = "/dev/bus/usb/001/045"
    private val address = 129
    private val endpointNumber = 1
    private val direction = 128
    private val attributes = 2
    private val type = 2
    private val maxPacketSize = 64
    private val interval = 0
    private var usbDevice1 = mockk<UsbDevice>(relaxed = true)
    private val usbConfiguration = mockk<UsbConfiguration>()
    private val usbInterface = mockk<UsbInterface>()
    private val usbEndpoint = mockk<UsbEndpoint>(relaxed = true)
    private val mockUsbSerialPort = mockk<UsbSerialPort>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    lateinit var serialRepository: SerialRepository
    lateinit var terminalViewModel: TerminalViewModel
    private val testValue = "Test Data"
    val testData = testValue.toByteArray()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        initUsbConfiguration()
        initLog()
        initViewModel()
        val binder = serviceRule.bindService(
            Intent(
                ApplicationProvider.getApplicationContext(),
                SerialService::class.java,
            ),
        )
        terminalViewModel.init(binder)
        terminalViewModel.attachListener(this@SerialServiceIntegrationTest)
        terminalViewModel.register()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cancel()
    }

    @Test fun shouldReadDataWhenServiceIsBound() = runTest {
        // Given
        val portNum = 0
        val baudRate = 115200
        coEvery { mockUsbSerialPort.read(any(), any()) } returns testData.size

        // When
        terminalViewModel.connect(portNum, baudRate, deviceId, mockUsbSerialPort)

        // Then
        terminalViewModel.viewState.test {
            val value = awaitItem()
            assert(value.connected == Connected.True)
        }
    }

    private fun initLog() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    private fun initUsbConfiguration() {
        every { usbDevice1.deviceName } returns (deviceName)
        every { usbDevice1.deviceId } returns (deviceId)
        every { usbDevice1.vendorId } returns (vendorId)
        every { usbDevice1.productId } returns (productId)
        every { usbDevice1.manufacturerName } returns (manufactureName)
        every { usbDevice1.version } returns (version)
        every { usbDevice1.serialNumber } returns (serialNumber)
        every { usbDevice1.deviceClass } returns (0)
        every { usbDevice1.deviceSubclass } returns (0)
        every { usbDevice1.deviceProtocol } returns (0)
        every { usbDevice1.configurationCount } returns (1)
        every { usbDevice1.interfaceCount } returns (1)
        every { usbConfiguration.interfaceCount } returns (1)
        every { usbConfiguration.name } returns null
        every { usbConfiguration.id } returns (1)
        every { usbConfiguration.isRemoteWakeup } returns false
        every { usbConfiguration.isSelfPowered } returns false
        every { usbConfiguration.maxPower } returns 100
        every { usbInterface.id } returns 0
        every { usbInterface.name } returns usbInterfaceName
        every { usbInterface.interfaceProtocol } returns 0
        every { usbInterface.interfaceClass } returns USB_SUBCLASS_VENDOR_SPEC
        every { usbInterface.endpointCount } returns 1
        every { usbEndpoint.endpointNumber } returns endpointNumber
        every { usbEndpoint.attributes } returns attributes
        every { usbEndpoint.direction } returns direction
        every { usbEndpoint.interval } returns interval
        every { usbEndpoint.address } returns address
        every { usbEndpoint.maxPacketSize } returns maxPacketSize
        every { usbEndpoint.type } returns type
        every { usbInterface.getEndpoint(0) } returns usbEndpoint
        every { usbInterface.interfaceSubclass } returns 0
        every { usbInterface.alternateSetting } returns 0
        every { usbConfiguration.getInterface(0) } returns usbInterface
        every { usbDevice1.getConfiguration(0) } returns usbConfiguration
        every { usbDevice1.getInterface(0) } returns usbInterface
        every { usbManager.deviceList } returns
            hashMapOf(deviceId.toString() to usbDevice1)
        every { mockUsbSerialPort.open(any()) } just Runs
        every { mockUsbSerialPort.setParameters(any(), any(), any(), any()) } just Runs
        every { mockUsbSerialPort.dtr = true } just Runs
        every { mockUsbSerialPort.rts = true } just Runs
        every { mockUsbSerialPort.readEndpoint } returns usbEndpoint
    }

    private fun initViewModel() {
        serialRepository = SerialRepositoryImpl(usbManager, context)
        terminalViewModel = TerminalViewModel(serialRepository)
    }

    override fun onSerialConnect() {
        println("serial connect")
    }

    override fun onSerialConnectError(e: Exception?) {
        println("serial connect error")
    }

    override fun onSerialRead(data: ByteArray?) {
        assert(data?.isNotEmpty() == true)
        println("serial read data one value" + data?.toString(Charsets.UTF_8))
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        assert(datas?.isNotEmpty() == true)
        println("serial read data" + datas?.firstOrNull()?.toString(Charsets.UTF_8))
    }

    override fun onSerialIoError(e: Exception?) {
        println("serial connect error:" + e?.message)
    }
}
