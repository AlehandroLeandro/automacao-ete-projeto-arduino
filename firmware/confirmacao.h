#ifndef ETE_CONFIRMACAO_H
#define ETE_CONFIRMACAO_H
#include <ArduinoJson.h>
#include <string.h>
inline bool confirmacaoIndividual(JsonDocument &doc, const char *id, bool altura, bool ph) {
  JsonArray resultados = doc["results"].as<JsonArray>();
  if (resultados.isNull()) return false;
  unsigned int encontradas = 0;
  for (JsonObject resultado : resultados) {
    const char *rid = resultado["id"] | "";
    if (strcmp(id, rid) != 0) continue;
    if (!resultado["accepted"].is<JsonArray>() || !resultado["rejected"].is<JsonArray>() ||
        !resultado["discarded"].is<JsonArray>() || !resultado["duplicate"].is<bool>()) return false;
    unsigned int vistos = 0;
    const unsigned int esperados = 1u | (altura ? 2u : 0u) | (ph ? 4u : 0u);
    auto registrar = [&](const char *nome) {
      unsigned int bit = strcmp(nome, "rawPh") == 0 ? 1u :
                         strcmp(nome, "heightCm") == 0 ? 2u :
                         strcmp(nome, "treatedPh") == 0 ? 4u : 0u;
      if (!bit || !(esperados & bit) || (vistos & bit)) return false;
      vistos |= bit;
      return true;
    };
    JsonArray aceitos = resultado["accepted"].as<JsonArray>();
    for (JsonVariant campo : aceitos) {
      if (!campo.is<const char *>()) return false;
      if (!registrar(campo.as<const char *>())) return false;
    }
    for (const char *tipo : {"rejected", "discarded"}) {
      JsonArray campos = resultado[tipo].as<JsonArray>();
      for (JsonVariant campo : campos) {
        if (!campo["field"].is<const char *>() || !campo["reason"].is<const char *>()) return false;
        if (!registrar(campo["field"].as<const char *>())) return false;
      }
    }
    if (vistos != esperados) return false;
    ++encontradas;
  }
  return encontradas == 1;
}
#endif
