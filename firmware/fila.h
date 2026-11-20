#ifndef ETE_FILA_H
#define ETE_FILA_H
#include <stdint.h>
#include <stddef.h>
#include <string.h>

// Byte layout is fixed; a record is visible only after its final marker is written.
struct __attribute__((packed)) RegistroFila {
  uint64_t sequencia;
  uint32_t inicio;
  uint32_t instanteMs;
  int16_t alturaCenti;
  int16_t phCenti;
  uint8_t flags;
  uint16_t checksum;
  uint8_t estado;
};
static_assert(sizeof(RegistroFila) == 24, "formato persistente inesperado");

static const uint8_t FILA_PENDENTE = 0xa5;
static const uint8_t FILA_CONFIRMADO = 0x5a;
static const uint8_t FILA_ALTURA = 1;
static const uint8_t FILA_PH = 2;

inline uint16_t checksumRegistro(const RegistroFila &r) {
  const uint8_t *p = reinterpret_cast<const uint8_t *>(&r);
  uint16_t crc = 0xffff;
  for (size_t i = 0; i < offsetof(RegistroFila, checksum); ++i) {
    crc ^= uint16_t(p[i]) << 8;
    for (uint8_t bit = 0; bit < 8; ++bit)
      crc = (crc & 0x8000) ? uint16_t((crc << 1) ^ 0x1021) : uint16_t(crc << 1);
  }
  return crc;
}

template<class Storage> class Fila {
  Storage &storage;
  uint16_t quantidade;
  bool ler(uint16_t pos, RegistroFila &r) const {
    uint8_t *p = reinterpret_cast<uint8_t *>(&r);
    for (size_t i = 0; i < sizeof(r); ++i) p[i] = storage.read(pos * sizeof(r) + i);
    return (r.estado == FILA_PENDENTE || r.estado == FILA_CONFIRMADO) &&
           r.sequencia != 0 && r.checksum == checksumRegistro(r);
  }
public:
  explicit Fila(Storage &s) : storage(s), quantidade(s.length() / sizeof(RegistroFila)) {}
  uint16_t capacidade() const { return quantidade; }
  uint64_t maiorSequencia() const {
    uint64_t maior = 0; RegistroFila r;
    for (uint16_t i = 0; i < quantidade; ++i)
      if (ler(i, r) && r.sequencia > maior) maior = r.sequencia;
    return maior;
  }
  bool inserir(RegistroFila r) {
    if (quantidade < 2) return false;
    uint64_t maior = maiorSequencia();
    if (maior == UINT64_MAX) return false;
    int destino = -1; uint64_t menor = UINT64_MAX; RegistroFila atual;
    for (uint16_t i = 0; i < quantidade; ++i) {
      if (!ler(i, atual)) { destino = i; break; }
      if (atual.estado == FILA_CONFIRMADO && atual.sequencia < menor && atual.sequencia != maior) {
        menor = atual.sequencia; destino = i;
      }
    }
    if (destino < 0) return false;
    r.sequencia = maior + 1; r.checksum = checksumRegistro(r); r.estado = FILA_PENDENTE;
    size_t base = size_t(destino) * sizeof(r);
    if (!storage.beginWrite(base)) return false;
    storage.update(base + offsetof(RegistroFila, estado), 0);
    const uint8_t *p = reinterpret_cast<const uint8_t *>(&r);
    for (size_t i = 0; i < offsetof(RegistroFila, estado); ++i) storage.update(base + i, p[i]);
    storage.update(base + offsetof(RegistroFila, estado), FILA_PENDENTE);
    if (!storage.commitWrite()) return false;
    RegistroFila verificacao;
    return ler(destino, verificacao) && verificacao.sequencia == r.sequencia && verificacao.estado == FILA_PENDENTE;
  }
  bool antiga(RegistroFila &r, uint16_t &pos) const {
    bool achou = false; RegistroFila atual;
    for (uint16_t i = 0; i < quantidade; ++i)
      if (ler(i, atual) && atual.estado == FILA_PENDENTE && (!achou || atual.sequencia < r.sequencia)) {
        r = atual; pos = i; achou = true;
      }
    return achou;
  }
  bool confirmar(uint16_t pos, uint64_t sequencia) {
    RegistroFila r;
    if (pos >= quantidade || !ler(pos, r) || r.estado != FILA_PENDENTE || r.sequencia != sequencia) return false;
    const size_t endereco = size_t(pos) * sizeof(r) + offsetof(RegistroFila, estado);
    if (!storage.beginWrite(endereco)) return false;
    storage.update(endereco, FILA_CONFIRMADO);
    if (!storage.commitWrite()) return false;
    return ler(pos, r) && r.estado == FILA_CONFIRMADO;
  }
};
#endif
