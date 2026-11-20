#define ETE_WIFI_SSID "ssid"
#define ETE_WIFI_PASSWORD "password"
#define ETE_SERVER_HOST "example.test"
#define ETE_SERVER_PORT 443
#define ETE_DEVICE_KEY "device-key"
#include "../rede.h"
#include <cassert>
WifiStub WiFi;
TestNetwork network;
unsigned long fakeMillis = 0;
static std::string response(const char *status, const char *body) {
  return std::string("HTTP/1.0 ") + status + "\r\n\r\n" + body;
}
static void tick(Rede &rede, unsigned long time) { fakeMillis = time; rede.tick(time); }
static bool pending(Fila<FlashFila> &f, uint64_t expected) {
  RegistroFila r = {}; uint16_t pos = 0;
  return f.antiga(r, pos) && r.sequencia == expected;
}
int main() {
  FlashFila flash; assert(flash.begin()); Fila<FlashFila> fila(flash);
  RegistroFila r = {}; r.inicio = 1; r.flags = FILA_ALTURA | FILA_PH;
  r.alturaCenti = 123; r.phCenti = 456;
  assert(fila.inserir(r)); assert(fila.inserir(r));
  Rede rede(fila, 1);
  WiFi.online = false; tick(rede, 0); assert(WiFi.attempts == 1 && pending(fila, 1));
  WiFi.online = true;
  network.responses.push_back(response("200 OK", "{\"accessToken\":\"token\",\"expiresInSeconds\":120}"));
  tick(rede, 15000); tick(rede, 15100);
  assert(network.requests.back().find("POST /api/auth/device-login ") == 0);
  assert(network.requests.back().find("device-key") != std::string::npos);
  network.responses.push_back(response("401 Unauthorized", "{}"));
  tick(rede, 16100); tick(rede, 16200); assert(pending(fila, 1));
  assert(network.requests.back().find("Authorization: Bearer token") != std::string::npos);
  network.responses.push_back(response("200 OK", "{\"accessToken\":\"new-token\",\"expiresInSeconds\":120}"));
  tick(rede, 31200); tick(rede, 31300);
  assert(network.requests.back().find("POST /api/auth/device-login ") == 0);
  network.responses.push_back(response("200 OK", "{\"results\":[{\"id\":\"1\",\"accepted\":[\"rawPh\"],\"rejected\":[],\"discarded\":[],\"duplicate\":false}]}"));
  tick(rede, 32300); tick(rede, 32400); assert(pending(fila, 1));
  assert(network.requests.back().find("\"id\":\"1\"") != std::string::npos);
  network.responses.push_back(response("200 OK", "{\"results\":[{\"id\":\"1\",\"accepted\":[\"rawPh\",\"heightCm\",\"treatedPh\"],\"rejected\":[],\"discarded\":[],\"duplicate\":true}]}"));
  tick(rede, 47400); tick(rede, 47500); assert(pending(fila, 2));
  FlashFila restarted; assert(restarted.begin()); Fila<FlashFila> restored(restarted);
  assert(pending(restored, 2));
  network.hold = true;
  tick(rede, 48500); tick(rede, 56600); assert(pending(fila, 2));
}
