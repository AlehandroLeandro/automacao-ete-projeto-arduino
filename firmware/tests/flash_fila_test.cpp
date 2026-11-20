#include "../flash_fila.h"
#include "../fila.h"
#include <cassert>
int main() {
  FlashFila flash; assert(flash.begin()); Fila<FlashFila> fila(flash); RegistroFila r = {}; uint16_t pos;
  assert(fila.capacidade() == 168);
  assert(fila.inserir(r));
  DataFlashBlockDevice::getInstance().fault = 2;
  assert(!fila.inserir(r));
  DataFlashBlockDevice::getInstance().fault = 0;
  FlashFila reiniciada; assert(reiniciada.begin()); Fila<FlashFila> recuperada(reiniciada);
  assert(recuperada.antiga(r, pos) && r.sequencia == 1);
  assert(recuperada.inserir(r));
  assert(recuperada.confirmar(pos, 1));
  FlashFila outra; assert(outra.begin()); Fila<FlashFila> segunda(outra);
  assert(segunda.antiga(r, pos) && r.sequencia == 2);
}
