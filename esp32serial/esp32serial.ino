int ledPin = 2;
String message;
void setup() {
  Serial.begin(115200);
  Serial.print("Hello Access Next team!\n");
  pinMode(ledPin, OUTPUT);
}
void loop() {
  // digitalWrite(ledPin, HIGH);
  // delay(1000);
  // digitalWrite(ledPin, LOW);
  // delay(1000);
  if(Serial.available()) {
   char incomingChar = Serial.read();
    if (incomingChar != '\n'){
      message += String(incomingChar);
    }
    else{
      message = "";
    }
    if(message == "on") {
      digitalWrite(ledPin, HIGH);
    } else if(message == "off") {
      digitalWrite(ledPin, LOW);
    }
    Serial.write(incomingChar); 
  }
}