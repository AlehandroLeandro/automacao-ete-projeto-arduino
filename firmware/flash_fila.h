#ifndef ETE_FLASH_FILA_H
#define ETE_FLASH_FILA_H
#include <DataFlashBlockDevice.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>

// Four independent pairs of 1 KiB blocks; only the older copy is erased.
class FlashFila {
  static const size_t BLOCO = 1024, CABECALHO = 16, DADOS = BLOCO - CABECALHO, PARES = 4;
  static const uint32_t MAGIA = 0x45544531;
  DataFlashBlockDevice &flash;
  alignas(4) uint8_t pagina[BLOCO];
  uint8_t dados[PARES * DADOS];
  uint8_t ativo[PARES];
  uint64_t geracao[PARES];
  bool pronto;
  int alterado;
  static uint32_t crc(const uint8_t *p, size_t n) {
    uint32_t c = 0xffffffff;
    while (n--) { c ^= *p++; for (int b = 0; b < 8; ++b) c = (c >> 1) ^ (0xedb88320u & (0u - (c & 1u))); }
    return ~c;
  }
  bool examinar(size_t bloco, uint64_t &gen, bool &vazio) {
    if (flash.read(pagina, bloco * BLOCO, BLOCO)) return false;
    vazio = true; for (size_t i = 0; i < BLOCO; ++i) if (pagina[i] != 0xff) { vazio = false; break; }
    uint32_t magic, guardado; memcpy(&magic, pagina, 4); memcpy(&gen, pagina + 4, 8); memcpy(&guardado, pagina + 12, 4);
    uint32_t calculado = crc(pagina + CABECALHO, DADOS) ^ crc(pagina, 12);
    return magic == MAGIA && gen != 0 && guardado == calculado;
  }
public:
  FlashFila() : flash(DataFlashBlockDevice::getInstance()), pronto(false), alterado(-1) { memset(ativo, 0xff, sizeof(ativo)); memset(geracao, 0, sizeof(geracao)); }
  bool begin() {
    if (flash.size() != 8192 || flash.get_erase_size() != BLOCO || flash.get_program_size() != BLOCO) return false;
    for (size_t p = 0; p < PARES; ++p) {
      uint64_t a = 0, b = 0; bool va = false, vb = false;
      bool ca = examinar(p * 2, a, va); bool cb = examinar(p * 2 + 1, b, vb);
      if (!ca && !cb && !(va && vb)) return false;
      if (ca || cb) {
        ativo[p] = cb && (!ca || b > a) ? 1 : 0; geracao[p] = ativo[p] ? b : a;
        if (flash.read(dados + p * DADOS, (p * 2 + ativo[p]) * BLOCO + CABECALHO, DADOS)) return false;
      } else memset(dados + p * DADOS, 0xff, DADOS);
    }
    return pronto = true;
  }
  size_t length() const { return pronto ? PARES * DADOS : 0; }
  uint8_t read(size_t endereco) {
    return pronto && endereco < sizeof(dados) ? dados[endereco] : 0xff;
  }
  bool beginWrite(size_t endereco) {
    size_t par = endereco / DADOS;
    if (!pronto || par >= PARES || alterado >= 0 || geracao[par] == UINT64_MAX) return false;
    if (ativo[par] == 0xff) memset(pagina, 0xff, BLOCO);
    else if (flash.read(pagina, (par * 2 + ativo[par]) * BLOCO, BLOCO)) return false;
    alterado = int(par); return true;
  }
  void update(size_t endereco, uint8_t valor) {
    if (alterado >= 0 && endereco / DADOS == size_t(alterado)) pagina[CABECALHO + endereco % DADOS] = valor;
  }
  bool commitWrite() {
    if (alterado < 0) return false;
    size_t par = size_t(alterado); alterado = -1;
    uint8_t alvo = ativo[par] == 0 ? 1 : 0;
    uint64_t nova = geracao[par] + 1; uint32_t magic = MAGIA;
    memcpy(pagina, &magic, 4); memcpy(pagina + 4, &nova, 8);
    uint32_t soma = crc(pagina + CABECALHO, DADOS) ^ crc(pagina, 12); memcpy(pagina + 12, &soma, 4);
    size_t endereco = (par * 2 + alvo) * BLOCO;
    if (flash.erase(endereco, BLOCO) || flash.program(pagina, endereco, BLOCO)) return false;
    uint64_t verificada; bool vazio;
    if (!examinar(par * 2 + alvo, verificada, vazio) || verificada != nova) return false;
    memcpy(dados + par * DADOS, pagina + CABECALHO, DADOS);
    ativo[par] = alvo; geracao[par] = nova; return true;
  }
};
#endif
