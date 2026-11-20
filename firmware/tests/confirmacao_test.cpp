#include "../confirmacao.h"
#include <cassert>
int main() {
  JsonDocument doc;
  assert(!deserializeJson(doc, "{\"results\":[{\"id\":\"7\",\"accepted\":[\"rawPh\"],\"rejected\":[],\"discarded\":[],\"duplicate\":true}]}"));
  assert(confirmacaoIndividual(doc, "7", false, false));
  assert(!confirmacaoIndividual(doc, "7", true, false));
  assert(!confirmacaoIndividual(doc, "8", false, false));
  doc.clear();
  assert(!deserializeJson(doc, "{\"results\":[{\"id\":\"7\",\"accepted\":[],\"rejected\":[],\"duplicate\":false}]}"));
  assert(!confirmacaoIndividual(doc, "7", false, false));
  doc.clear();
  assert(!deserializeJson(doc, "{\"results\":[]}"));
  assert(!confirmacaoIndividual(doc, "7", false, false));
  doc.clear();
  assert(!deserializeJson(doc, "{\"results\":[{\"id\":\"7\",\"accepted\":[\"rawPh\",\"heightCm\"],\"rejected\":[{\"field\":\"treatedPh\",\"reason\":\"invalid\"}],\"discarded\":[],\"duplicate\":false}]}"));
  assert(confirmacaoIndividual(doc, "7", true, true));
  assert(!confirmacaoIndividual(doc, "7", true, false));
  doc.clear();
  assert(!deserializeJson(doc, "{\"results\":[{\"id\":\"7\",\"accepted\":[\"rawPh\",\"rawPh\"],\"rejected\":[],\"discarded\":[],\"duplicate\":false}]}"));
  assert(!confirmacaoIndividual(doc, "7", false, false));
}
