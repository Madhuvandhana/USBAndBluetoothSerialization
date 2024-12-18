# Android Serail Bidirectional Communication

## Overview

This project facilitates bidirectional communication between Android devices and an ESP32 module using COM/Serial ports. The communication system is designed to work with the Genio 700 EVK Kit, other serial drivers mentioned below and Raspberry Pi connections.

## ESP32 Setup

### Development Environment

To set up the ESP32 module, follow these steps:

1. Download and install the Arduino IDE.
2. Open the IDE, navigate to **File > Preferences**, and add the following URL to the Additional Boards Manager URLs:
   ```
   https://dl.espressif.com/dl/package_esp32_index.json
   ```
3. Go to **Tools > Board > Boards Manager**, search for "esp32" and install the Espressif Systems ESP32 boards.

### Arduino IDE Code

Use the provided `esp32serial.ino` file in the `/poc_axn_usb_serialization/esp32serial/` directory.

1. Open the `esp32serial.ino` file in the Arduino IDE.
2. Verify and upload the code to the ESP32 module.

### Test Communication

1. Connect the ESP32 to the PC using a USB cable.
2. Open the Arduino Serial Monitor or use a terminal program like Screen with the appropriate baud rate (115200).
3. Test bidirectional communication by sending and receiving data.

## Android - ESP32 Communication

### Android Application

This Android application provides a line-oriented terminal/console for devices with a serial/UART interface connected through a USB-to-serial-converter.

#### Features:

- **Permission Handling:** The app requests and handles necessary permissions for device connection.
- **Foreground Service:** A service buffers received data even when the app is rotating or in the background.
- **USB Connection:** Connect the Android device to the ESP-Wroom-32 using a USB-C hub to micro USB.
- **Bidirectional Communication:** Verify bidirectional communication by sending and receiving strings.

#### Supported USB to Serial Converters:

- FTDI FT232, FT2232
- Prolific PL2303
- Silabs CP2102, CP2105
- Qinheng CH340, CH341
- Devices implementing the USB CDC protocol like Arduino using ATmega32U4, Digispark, BBC micro:bit.

#### Installation Steps:

1. Download and install the Arduino IDE on your PC.
2. Follow the steps in the video tutorial [here](https://www.youtube.com/watch?v=wia2sUjNpwI) or refer to the official [Arduino IDE documentation](https://docs.arduino.cc/software/ide-v2/tutorials/ide-v2-board-manager).
3. Download the Android application and install it on your device.

#### Video Demo
| Demo Description                                                          | Video                                                                                           |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| LED On and Off                                                            | <img src="/samples/led.gif" alt="Demo showcasing LED on and off" height = "50"  width="50"/>                  |
| Bidirectional Communication with Character Echoing                         | <img src="/samples/echo.gif" alt="Demo showcasing bidirectional communication" height = "50" width="50"/>    |
| Bluetooth Communication for Controlling LED                               | <img src="/samples/bluetooth.gif" alt="Demo showcasing bluetooth communication" height = "50" width="50"/>  |


#### Usage:

1. Clone the Git repository by executing the following command: $ git clone git@bitbucket.org:nortek-control/poc_axn_usb_serialization.git.
2. Navigate to the /poc_axn_usb_serialization/USBSerialization directory in Android Studio. Run and install the application on your Android phone/tablet.
1. Connect the Android device to the ESP-Wroom-32 using a USB-C hub to micro USB.
2. Open the installed Android application.
3. Grant necessary permissions for device connection.
4. Verify bidirectional communication by sending and receiving strings.

For more details, refer to the provided resources and documentation.
