#include "../fila.h"
#include <cassert>
#include <vector>
struct Memoria {
  std::vector<uint8_t> bytes;
  explicit Memoria(size_t n) : bytes(n, 0xff) {}
  size_t length() { return bytes.size(); }
  uint8_t read(size_t i) { return bytes.at(i); }
  bool beginWrite(size_t) { return true; }
  void update(size_t i, uint8_t v) { bytes.at(i) = v; }
  bool commitWrite() { return true; }
};
int main() {
  Memoria memoria(3 * sizeof(RegistroFila));
  Fila<Memoria> fila(memoria); RegistroFila amostra = {}, lida = {}; uint16_t pos = 0;
  assert(fila.inserir(amostra)); assert(fila.inserir(amostra)); assert(fila.inserir(amostra));
  assert(!fila.inserir(amostra));
  Fila<Memoria> reiniciada(memoria);
  assert(reiniciada.antiga(lida, pos) && lida.sequencia == 1);
  assert(!reiniciada.confirmar(pos, 2)); // resposta de outra amostra nao remove
  assert(reiniciada.antiga(lida, pos) && lida.sequencia == 1); // resposta perdida mantem pendente
  assert(reiniciada.confirmar(pos, 1));
  assert(!reiniciada.confirmar(pos, 1));
  assert(reiniciada.inserir(amostra));
  Fila<Memoria> novamente(memoria);
  assert(novamente.maiorSequencia() == 4);
  assert(novamente.antiga(lida, pos) && lida.sequencia == 2);
  assert(novamente.confirmar(pos, 2));
  assert(novamente.antiga(lida, pos) && lida.sequencia == 3);
  assert(novamente.confirmar(pos, 3));
  assert(novamente.antiga(lida, pos) && lida.sequencia == 4);
  assert(novamente.confirmar(pos, 4));
  assert(novamente.inserir(amostra) && novamente.maiorSequencia() == 5);
}
