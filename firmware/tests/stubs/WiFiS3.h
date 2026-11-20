#ifndef TEST_WIFI_H
#define TEST_WIFI_H
#include "Arduino.h"
#include <deque>
#include <vector>
#define ARDUINOJSON_ENABLE_ARDUINO_STRING 1
#define WL_CONNECTED 3
struct WifiStub {
  bool online = true;
  int attempts = 0;
  int status() { return online ? WL_CONNECTED : 0; }
  void setTimeout(unsigned long) {}
  void begin(const char *, const char *) { ++attempts; }
  unsigned long getTime() { return 1704067200UL; }
};
extern WifiStub WiFi;
struct TestNetwork {
  std::deque<std::string> responses;
  std::vector<std::string> requests;
  bool connectOk = true;
  bool hold = false;
};
extern TestNetwork network;
class WiFiSSLClient {
  std::string response;
  size_t offset = 0;
public:
  void setConnectionTimeout(unsigned long) {}
  bool connect(const char *, unsigned int) {
    if (!network.connectOk) return false;
    network.requests.emplace_back();
    response = network.responses.empty() ? "" : network.responses.front();
    if (!network.responses.empty()) network.responses.pop_front();
    offset = 0; return true;
  }
  void stop() {}
  void print(const char *s) { network.requests.back() += s; }
  void print(const String &s) { network.requests.back() += s; }
  void println(const char *s) { print(s); print("\r\n"); }
  void println(const String &s) { print(s); print("\r\n"); }
  void println(size_t n) { network.requests.back() += std::to_string(n) + "\r\n"; }
  void println() { print("\r\n"); }
  int available() { return int(response.size() - offset); }
  int read() { return response[offset++]; }
  bool connected() { return network.hold || offset < response.size(); }
};
#endif
