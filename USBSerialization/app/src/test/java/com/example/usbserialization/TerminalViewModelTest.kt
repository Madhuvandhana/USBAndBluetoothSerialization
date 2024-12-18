package com.example.usbserialization

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import app.cash.turbine.test
import com.example.usbserialization.data.SerialSocket
import com.example.usbserialization.domain.SerialRepository
import com.example.usbserialization.presentation.ChatData
import com.example.usbserialization.presentation.TerminalViewModel
import com.example.usbserialization.presentation.ViewIntent
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.example.usbserialization.presentation.utils.TextUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException

/**
 * @author madhu.kumar
 * Created 1/5/24 at 10:33 AM
 */
@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class TerminalViewModelTest {

    // Mock dependencies
    private val serialRepository = mockk<SerialRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val context = mockk<Context>()
    private val connection = mockk<UsbDeviceConnection>()
    private val serialPort = mockk<UsbSerialPort>()

    lateinit var terminalViewModel: TerminalViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        initLog()
        terminalViewModel = TerminalViewModel(serialRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cancel()
    }

    @Test
    fun `when usb serial connection is established should succeed`() = runTest {
        // Given
        val portNum = 1
        val baudRate = 115200
        val deviceId = 123
        coEvery { serialRepository.connect(portNum, baudRate, deviceId, any(), any()) } just Runs

        // When
        terminalViewModel.connect(portNum, baudRate, deviceId)

        // Then
        coVerify { serialRepository.connect(portNum, baudRate, deviceId, any(), any()) }
    }

    @Test
    fun `when usb serial is disconnected should be successful`() = runTest {
        // Given
        coEvery { serialRepository.disconnect() } just Runs

        // When
        terminalViewModel.disconnect()

        // Then
        coVerify { serialRepository.disconnect() }
    }

    @Test
    fun `when write with valid data should write to the serial port`() {
        // Given
        val serialSocket = spyk(SerialSocket(context, connection, serialPort))
        val testData = "Test Data".toByteArray()
        every { serialPort.write(testData, any()) } just Runs

        // When
        serialSocket.write(testData)

        // Then
        verify { serialPort.write(testData, any()) }
    }

    @Test(expected = IOException::class)
    fun `when write with valid data and no serial connection should throw error`() {
        // Given
        val serialSocket = spyk(SerialSocket(context, connection, null))

        // When
        serialSocket.write("Test Data".toByteArray())

        // Then
        // Exception
    }

    @Test
    fun `when write with valid data should succeed`() = runTest {
        // Given
        val sendText = "Test Data"
        val testData = (sendText + TextUtil.carriageReturn).toByteArray()
        val portNum = 1
        val baudRate = 115200
        val deviceId = 123
        coEvery { serialRepository.connect(portNum, baudRate, deviceId, any(), any()) } just Runs
        coEvery { serialRepository.write(testData) } just Runs

        // When
        terminalViewModel.connect(portNum, baudRate, deviceId)
        terminalViewModel.sendEvent(ViewIntent.Send(sendText))

        // Then
        coVerify { serialRepository.write(testData) }
        terminalViewModel.viewState.test {
            assertTrue(awaitItem().chatList.contains(ChatData(sendText, true)))
        }
    }

    private fun initLog() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }
}
