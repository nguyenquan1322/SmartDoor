#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Adafruit_Fingerprint.h>
#include <Keypad.h>
#include <time.h>


// ---------------- WiFi ----------------
#define WIFI_SSID     "CHIEN_tq_1_5G"
#define WIFI_PASS     "Chiendeptrai1"

// ---------------- Firebase ----------------
#define FIREBASE_HOST "smartdooriot-b970c-default-rtdb.firebaseio.com"
#define DEVICE_ID     "esp32-frontdoor-01"

// ---------------- Pin ----------------
#define OPEN_LED_PIN   2
#define CLOSE_LED_PIN  15
// #define INT_PIN        4
#define RXD2      16
#define TXD2      17

// ---------------- Fingerprint ----------------
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&Serial2);

// ---------------- Keypad ----------------
const byte ROWS = 4, COLS = 4;
char keys[ROWS][COLS] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
byte rowPins[ROWS] = {21, 18, 19, 5};
byte colPins[COLS] = {23, 22, 27, 12};
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

// ---------------- Global Variables ----------------
String currentUser = "unknown";
String password = "1234";
int id = 128;
bool requirePasswordMode = false;
// volatile bool extPasswordRequest = false;

int wrongFingerCount = 0;
const int WRONG_FINGER_LIMIT = 3;

bool doorOpen = false;
unsigned long doorOpenedAt = 0;
const unsigned long DOOR_OPEN_MS = 5000;

unsigned long lastFirebasePoll = 0;
const unsigned long FIREBASE_POLL_INTERVAL = 3000;

String inputBuffer = "";
unsigned long lastKeyPress = 0;
const unsigned long KEYPAD_TIMEOUT = 20000;


const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 7 * 3600;  // GMT+7 (Việt Nam)
const int daylightOffset_sec = 0;

// ---------------- Helper ----------------
String firebaseUrl(const String &path) {
  return "https://" + String(FIREBASE_HOST) + path + ".json";
}

String httpGET(const String &url) {
  if (WiFi.status() != WL_CONNECTED) return String();
  HTTPClient http;
  http.begin(url);
  int code = http.GET();
  String payload = "";
  if (code == HTTP_CODE_OK) {
    payload = http.getString();
  } else {
    Serial.printf("HTTP GET failed, code: %d, url: %s\n", code, url.c_str());
  }
  http.end();
  payload.trim();
  if (payload.startsWith("\"")) payload.remove(0, 1);
  if (payload.endsWith("\"")) payload.remove(payload.length() - 1);
  return payload;
}

int httpPUT(const String &url, const String &body) {
  if (WiFi.status() != WL_CONNECTED) return -1;
  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  int code = http.PUT(body);
  http.end();
  if (code != HTTP_CODE_OK && code != HTTP_CODE_NO_CONTENT) {
    Serial.printf("HTTP PUT failed %d url:%s\n", code, url.c_str());
  }
  return code;
}

int httpPOST(const String &url, const String &body) {
  if (WiFi.status() != WL_CONNECTED) return -1;
  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  int code = http.POST(body);
  http.end();
  if (code != HTTP_CODE_OK && code != HTTP_CODE_CREATED) {
    Serial.printf("HTTP POST failed %d url:%s\n", code, url.c_str());
  }
  return code;
}

// 🔍 Lấy tên user được gán cho thiết bị
String getAssignedUser() {
  String path = "/Devices/" + String(DEVICE_ID) + "/assignedUser";
  String user = httpGET(firebaseUrl(path));
  if (user == "" || user == "null") user = "unknown";
  return user;
}
//---------------------------------
String getTimeString() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return "unknown_time";
  char buffer[32];
  strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", &timeinfo);
  return String(buffer);
}

//----------------------------------

// ---------------- Logging ----------------
void pushLog(const String &event, const String &msg) {
  DynamicJsonDocument doc(512);
  doc["timestamp"] = getTimeString();
  doc["event"] = event;
  doc["message"] = msg;
  doc["device"] = DEVICE_ID;
  String out; serializeJson(doc, out);
  String path = "/Logs/" + currentUser;
  httpPOST(firebaseUrl(path), out);
  Serial.println("[LOG] " + msg);
}

//--------------updatedoorstate------------------
void updateDoorState(String state) {
  String path = "/Devices/" + String(DEVICE_ID) + "/status";
  // store value as JSON string: "open" or "close"
  httpPUT(firebaseUrl(path), "\"" + state + "\"");
  Serial.println("[Status] " + state);
}

// ---------------- Door ----------------
void openDoor() {
  digitalWrite(OPEN_LED_PIN, HIGH);
  digitalWrite(CLOSE_LED_PIN, LOW);
  doorOpen = true;
  doorOpenedAt = millis();
  updateDoorState("open");
  pushLog("open_door", "open_doored");
}

void closeDoor() {
  digitalWrite(OPEN_LED_PIN, LOW);
  digitalWrite(CLOSE_LED_PIN, HIGH);
  doorOpen = false;
  updateDoorState("close");
  pushLog("door_close", "Door_closed");
}

// ---------------- Fingerprint ----------------
void fingerBegin() {
  Serial2.begin(57600, SERIAL_8N1, RXD2, TXD2);
  finger.begin(57600);
  if (finger.verifyPassword()) {
    Serial.println("Fingerprint sensor ready.");
  } else {
    Serial.println("Fingerprint sensor not detected!");
    pushLog("sensor_error", "Fingerprint sensor not detected!");
  }
}

uint8_t getFingerprintIDez() {
  uint8_t p = finger.getImage();
  if (p != FINGERPRINT_OK) return p;
  p = finger.image2Tz();
  if (p != FINGERPRINT_OK) return p;
  p = finger.fingerSearch();
  return p;
}

int tryRecognizeFinger() {
  uint8_t p = getFingerprintIDez();
  if (p == FINGERPRINT_OK) {
    pushLog("fingerprint_success", "ID=" + String(finger.fingerID));
    wrongFingerCount = 0;
    return finger.fingerID;
  }
  if (p == FINGERPRINT_NOTFOUND) return -1;
  return -2;
}

int enrollFingerprint() {
  uint8_t p = -1;
  Serial.println("dat ngon tay len");
  while (p != FINGERPRINT_OK){
    p = finger.getImage();
  }
  finger.image2Tz(1);
  if (p != FINGERPRINT_OK){
    pushLog("add_fingerprint" , "add_failed");
    return p;
  } 
  delay(1000);
  p=0;
  while (p != FINGERPRINT_NOFINGER){
    p = finger.getImage();
  } 

  Serial.println("Dat lai ngon tay len...");

  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
  }
  p = finger.image2Tz(2);

  if (p != FINGERPRINT_OK){
    pushLog("add_fingerprint" , "add_failed");
    return p;
  } 

  p = finger.createModel();

  if (p != FINGERPRINT_OK){
    pushLog("add_fingerprint" , "add_failed");
    return p;
  } 
  p = finger.storeModel(id);
  if (p == FINGERPRINT_OK) {
    Serial.println("Dang ky thanh cong!");
  } else {
    Serial.println("Loi khi luu van tay!");
  }
  pushLog("add_fingerprint", "add_enroll_success");

  DynamicJsonDocument doc(256);
  doc["username"] = currentUser;
  doc["addedAt"] = getTimeString();
  String out; serializeJson(doc, out);
  httpPUT(firebaseUrl("/Fingerprints/" + String(DEVICE_ID) + "/" + "fp_" + String(id)), out);
  return id;
}

bool deleteFingerprint(int id) {
  if (finger.deleteModel(id) == FINGERPRINT_OK) {
    pushLog("delete_fingerprint", "delete_success=" + String(id));
    httpPUT(firebaseUrl("/Fingerprints/" + String(DEVICE_ID) + "/" + "fp_" + String(id)), "null");
    return true;
  }
  pushLog("delete_fingerprint", "delete_failed=" + String(id));
  return false;
}

// ---------------- Keypad ----------------

void processKeypad() {
  inputBuffer = "";
  Serial.println("Nhap mat khau (nhan '#' de xac nhan):");

  while (true) {
    char k = keypad.getKey();

    if (k) {
      if (k == '#') {
        Serial.print("Ban da nhap: ");
        Serial.println(inputBuffer);

        if (inputBuffer == password) {
          pushLog("keypad_success", "Correct password");
          openDoor();
          requirePasswordMode = false;
          wrongFingerCount = 0;
        } else {
          pushLog("keypad_fail", "Wrong password");
        }
        inputBuffer = "";
        break; // Thoát khỏi vòng chờ khi đã nhập xong
      }
      else if (k == '*') {
        inputBuffer = ""; // Xóa toàn bộ nếu bấm *
        Serial.println("Nhap lai mat khau:");
      }
      else {
        inputBuffer += k;
        Serial.print("*"); // Ẩn ký tự để giống kiểu nhập pass
      }
    }

    delay(50); // Chống dội phím
  }
}


// ---------------- Firebase Command Handler ----------------
void handleFirebaseCommands() {
  String cmdPath = "/Commands/" + String(DEVICE_ID);
  String payload = httpGET(firebaseUrl(cmdPath));
  if (payload.length() == 0 || payload == "null") return;

  DynamicJsonDocument doc(2048);
  if (deserializeJson(doc, payload)) return;

  String type = doc["type"] | "";
  String value = doc["value"] | "";
  if (type == "") return;

  if (type == "open_door") {
    openDoor();
  }
  else if (type == "add_fingerprint") {
    id = value.toInt();
    int rid = enrollFingerprint();
    // if (rid > 0) 
    // pushLog("add_fp", "Added ID=" + String(id));
  }
  else if (type == "delete_fingerprint") {
    int id = value.toInt();
    deleteFingerprint(id);
  }
  else if (type == "change_pass") {
    if(value.length() > 3)
    password = value;
    Serial.println("change_pass_success");
    pushLog("change_pass", "change_success");
  }

  // Xóa command sau khi xử lý
  httpPUT(firebaseUrl(cmdPath), "null");
}

// ---------------- Setup ----------------
void setup() {
  Serial.begin(115200);
  pinMode(OPEN_LED_PIN, OUTPUT);
  pinMode(CLOSE_LED_PIN, OUTPUT);
  closeDoor();

  // pinMode(INT_PIN, INPUT_PULLUP);
  // attachInterrupt(digitalPinToInterrupt(INT_PIN), []() { extPasswordRequest = true; }, FALLING);

  fingerBegin();

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("Connecting WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected!");
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  Serial.println("Đang đồng bộ thời gian NTP...");

  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("Không thể lấy thời gian NTP!");
  } else {
    Serial.print("Thời gian hiện tại: ");
    Serial.println(&timeinfo, "%d/%m/%Y %H:%M:%S");
  }

  currentUser = getAssignedUser();
  Serial.println("Assigned user: " + currentUser);
}

// ---------------- Loop ----------------
void loop() {
  // Cập nhật user động
  static unsigned long lastUserCheck = 0;
  if (millis() - lastUserCheck > 10000) {
    String newUser = getAssignedUser();
    if (newUser != currentUser) {
      currentUser = newUser;
      Serial.println("User changed -> " + currentUser);
    }
    lastUserCheck = millis();
  }

  if (doorOpen && millis() - doorOpenedAt > DOOR_OPEN_MS) closeDoor();

  if (WiFi.status() == WL_CONNECTED && millis() - lastFirebasePoll > FIREBASE_POLL_INTERVAL) {
    lastFirebasePoll = millis();
    handleFirebaseCommands();
  }

  // if (extPasswordRequest) {
  //   extPasswordRequest = false;
  //   requirePasswordMode = true;
  //   pushLog("manual_request", "External interrupt triggered");
  // }

  if (requirePasswordMode) {
    
    processKeypad();
    return;
  }
//   else if (k == '*') {
//   inputBuffer = "";
//   requirePasswordMode = false;   // Thoát chế độ nhập pass
//   pushLog("keypad_cancel", "Exit password mode");
// }

  int fid = tryRecognizeFinger();
  if (fid > 0) {
    openDoor();
    delay(200);
  } else if (fid == -1) {
    wrongFingerCount++;
    pushLog("fingerprint_fail", "Fail count=" + String(wrongFingerCount));
    if (wrongFingerCount >= WRONG_FINGER_LIMIT) {
      Serial.println("nhap mat khau:");
      requirePasswordMode = true;
      delay(100);
    }
  } else {
    delay(200);
  }

  char k = keypad.getKey();
  if (k == '*') {
    Serial.println("nhap mat khau:");
    requirePasswordMode = true;
  }
}
