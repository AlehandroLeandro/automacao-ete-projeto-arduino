#ifndef ETE_REDE_H
#define ETE_REDE_H
#include <WiFiS3.h>
#include <ArduinoJson.h>
#include <time.h>
#include "confirmacao.h"
#include "fila.h"
#include "flash_fila.h"

// EXPERIMENTAL: WiFiS3 may block inside modem calls even with short timeouts.
class Rede {
  WiFiSSLClient client;
  Fila<FlashFila> &fila;
  const uint32_t inicio;
  String token, resposta;
  unsigned long proxima = 0, prazo = 0, expiracao = 0, ancoraMs = 0;
  uint32_t ancoraEpoch = 0;
  uint64_t enviada = 0;
  uint8_t flagsEnviadas = 0;
  uint16_t posicao = 0;
  enum Estado { OCIOSO, LOGIN, LOTE } estado = OCIOSO;
  void falha(unsigned long agora) { client.stop(); estado = OCIOSO; resposta = ""; proxima = agora + 15000UL; }
  bool vencido(unsigned long agora, unsigned long prazoMs) { return (int32_t)(agora - prazoMs) >= 0; }
  String id(uint64_t n) {
    char texto[21]; char *p = texto + sizeof(texto); *--p = '\0';
    do { *--p = char('0' + n % 10); n /= 10; } while (n);
    return String(p);
  }
  bool postar(const char *rota, const String &corpo, Estado operacao, unsigned long agora) {
    client.setConnectionTimeout(250);
    if (!client.connect(ETE_SERVER_HOST, ETE_SERVER_PORT)) return false;
    client.print("POST "); client.print(rota); client.println(" HTTP/1.0");
    client.print("Host: "); client.println(ETE_SERVER_HOST);
    client.println("Content-Type: application/json");
    client.println("Connection: close");
    if (operacao == LOTE) { client.print("Authorization: Bearer "); client.println(token); }
    client.print("Content-Length: "); client.println(corpo.length()); client.println(); client.print(corpo);
    resposta = ""; estado = operacao; prazo = agora + 8000UL; return true;
  }
  void processar(unsigned long agora) {
    int inicioCorpo = resposta.indexOf("\r\n\r\n");
    int codigo = resposta.startsWith("HTTP/1.") ? resposta.substring(9, 12).toInt() : 0;
    if (codigo == 401) token = "";
    if (codigo != 200 || inicioCorpo < 0) { falha(agora); return; }
    JsonDocument doc;
    if (deserializeJson(doc, resposta.c_str() + inicioCorpo + 4)) { falha(agora); return; }
    if (estado == LOGIN) {
      const char *t = doc["accessToken"] | "";
      unsigned long duracao = doc["expiresInSeconds"] | 0UL;
      if (!*t || duracao < 2) { falha(agora); return; }
      token = t; expiracao = agora + min(duracao - 1, 3600UL) * 1000UL;
    } else {
      String identificador = id(enviada);
      if (!confirmacaoIndividual(doc, identificador.c_str(), flagsEnviadas & FILA_ALTURA, flagsEnviadas & FILA_PH) || !fila.confirmar(posicao, enviada)) { falha(agora); return; }
    }
    client.stop(); estado = OCIOSO; resposta = ""; proxima = agora + 1000UL;
  }
public:
  Rede(Fila<FlashFila> &f, uint32_t boot) : fila(f), inicio(boot) {}
  void tick(unsigned long agora) {
    if (estado != OCIOSO) {
      while (client.available() && resposta.length() < 8192) resposta += char(client.read());
      if (resposta.length() >= 8192 || vencido(agora, prazo)) { falha(agora); return; }
      if (!client.connected() && !client.available()) processar(agora);
      return;
    }
    if (!vencido(agora, proxima)) return;
    if (WiFi.status() != WL_CONNECTED) {
      WiFi.setTimeout(250); WiFi.begin(ETE_WIFI_SSID, ETE_WIFI_PASSWORD);
      proxima = agora + 15000UL; return;
    }
    if (!ancoraEpoch) {
      unsigned long epoch = WiFi.getTime();
      if (epoch >= 1704067200UL) { ancoraEpoch = epoch; ancoraMs = millis(); }
    }
    if (token.length() && vencido(agora, expiracao)) token = "";
    JsonDocument doc; String corpo;
    if (!token.length()) {
      doc["key"] = ETE_DEVICE_KEY; serializeJson(doc, corpo);
      if (!postar("/api/auth/device-login", corpo, LOGIN, agora)) falha(agora);
      return;
    }
    RegistroFila r;
    if (!fila.antiga(r, posicao)) { proxima = agora + 5000UL; return; }
    enviada = r.sequencia;
    flagsEnviadas = r.flags;
    JsonObject item = doc["readings"].to<JsonArray>().add<JsonObject>();
    item["id"] = id(r.sequencia); item["rawPh"] = 2;
    if (r.flags & FILA_ALTURA) item["heightCm"] = r.alturaCenti / 100.0;
    if (r.flags & FILA_PH) item["treatedPh"] = r.phCenti / 100.0;
    item["timeEstimated"] = true;
    if (ancoraEpoch && r.inicio == inicio && r.instanteMs <= agora && agora >= ancoraMs) {
      int64_t epoch = int64_t(ancoraEpoch) + (int64_t(r.instanteMs) - ancoraMs) / 1000;
      if (epoch >= 0) {
        time_t t = time_t(epoch); struct tm tmUtc;
        if (gmtime_r(&t, &tmUtc)) {
          char iso[25]; strftime(iso, sizeof(iso), "%Y-%m-%dT%H:%M:%SZ", &tmUtc); item["measuredAt"] = String(iso);
        }
      }
    }
    serializeJson(doc, corpo);
    if (!postar("/api/device/readings/batch", corpo, LOTE, agora)) falha(agora);
  }
};
#endif
