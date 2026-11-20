#include "../medicoes.h"
#include <cassert>
#include <limits>

int main() {
  MedicaoSimulada m = montarMedicao(true, 25.0f, true, 12.345f);
  assert(m.rawPh == 2.0f && m.temTreatedPh && m.treatedPh == 10.0f);
  assert(m.temHeightCm && m.heightCm >= 12.34f && m.heightCm <= 12.35f);
  m = montarMedicao(true, -10.0f, true, 45.0f);
  assert(m.temTreatedPh && m.treatedPh == 0.0f && m.temHeightCm);
  m = montarMedicao(true, 100.0f, true, 45.01f);
  assert(m.temTreatedPh && m.treatedPh == 14.0f && !m.temHeightCm);
  m = montarMedicao(false, 25.0f, false, 10.0f);
  assert(!m.temTreatedPh && !m.temHeightCm && m.rawPh == 2.0f);
  m = montarMedicao(true, std::numeric_limits<float>::quiet_NaN(), true, -0.01f);
  assert(!m.temTreatedPh && !m.temHeightCm);
  m = montarMedicao(true, 21.11f, true, 0.0f);
  assert(m.temTreatedPh && m.treatedPh == 8.44f && m.temHeightCm);
}
