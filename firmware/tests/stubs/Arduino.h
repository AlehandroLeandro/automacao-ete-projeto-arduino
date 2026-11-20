#ifndef TEST_ARDUINO_H
#define TEST_ARDUINO_H
#include <string>
#include <cstdlib>
#include <algorithm>
#include <cstdint>
class String : public std::string {
public:
  using std::string::string;
  String() = default;
  String(const std::string &s) : std::string(s) {}
  String &operator=(const char *s) { assign(s ? s : ""); return *this; }
  int indexOf(const char *s) const { size_t n = find(s); return n == npos ? -1 : int(n); }
  bool startsWith(const char *s) const { return find(s) == 0; }
  String substring(size_t a, size_t b) const { return substr(a, b - a); }
  long toInt() const { return std::strtol(c_str(), nullptr, 10); }
  bool concat(const char *s) { append(s); return true; }
};
using std::min;
extern unsigned long fakeMillis;
inline unsigned long millis() { return fakeMillis; }
#endif
