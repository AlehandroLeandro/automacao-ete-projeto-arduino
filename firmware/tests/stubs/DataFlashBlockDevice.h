#ifndef TEST_FLASH_H
#define TEST_FLASH_H
#include <stdint.h>
#include <stddef.h>
#include <string.h>
class DataFlashBlockDevice {
public:
  uint8_t data[8192]; int fault;
  DataFlashBlockDevice() : fault(0) { memset(data, 0xff, sizeof(data)); }
  static DataFlashBlockDevice &getInstance() { static DataFlashBlockDevice d; return d; }
  int init() { return 0; }
  size_t size() const { return sizeof(data); }
  size_t get_erase_size() const { return 1024; }
  size_t get_program_size() const { return 1024; }
  int read(void *buf, size_t at, size_t n) { memcpy(buf, data + at, n); return 0; }
  int erase(size_t at, size_t n) { if (fault == 1) return 1; memset(data + at, 0xff, n); return 0; }
  int program(const void *buf, size_t at, size_t n) {
    if (fault == 2) { memcpy(data + at, buf, 64); return 1; }
    memcpy(data + at, buf, n); return 0;
  }
};
#endif
