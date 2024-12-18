#include "BluetoothSerial.h"
BluetoothSerial SerialBT;
char cmd;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32test"); //Bluetooth device name
  pinMode(2, OUTPUT);
  Serial.println("The device started, now you can pair it with bluetooth!");
}

void loop() {
  if (SerialBT.available()) {
       cmd = SerialBT.read();
  }
   if(cmd == '1') {
      digitalWrite(2, HIGH);
    } else if(cmd == '0') {
      digitalWrite(2, LOW);
    }
    delay(20);
  
}
