#ifndef ETE_MEDICOES_H
#define ETE_MEDICOES_H

#include <math.h>

struct MedicaoSimulada {
  float rawPh;
  float treatedPh;
  float heightCm;
  bool temTreatedPh;
  bool temHeightCm;
};

inline MedicaoSimulada montarMedicao(bool temperaturaValida, float temperaturaC,
                                    bool alturaValida, float alturaCm) {
  MedicaoSimulada medicao = {2.0f, 0.0f, 0.0f, false, false};
  if (temperaturaValida && isfinite(temperaturaC)) {
    float ph = temperaturaC / 2.5f;
    if (ph < 0.0f) ph = 0.0f;
    if (ph > 14.0f) ph = 14.0f;
    medicao.treatedPh = roundf(ph * 100.0f) / 100.0f;
    medicao.temTreatedPh = true;
  }
  if (alturaValida && isfinite(alturaCm) && alturaCm >= 0.0f && alturaCm <= 45.0f) {
    medicao.heightCm = roundf(alturaCm * 100.0f) / 100.0f;
    medicao.temHeightCm = true;
  }
  return medicao;
}

#endif
