#include <Servo.h>
#include <NewPing.h>
#include <math.h>
#include "firmware/medicoes.h"
#include "firmware/fila.h"
#include "firmware/flash_fila.h"
#if __has_include("firmware/config.h")
#include "firmware/config.h"
#else
// CONFIGURACAO DA REDE: preencha os campos entre aspas.
// Para manter as credenciais fora do sketch, copie firmware/config.example.h
// para firmware/config.h e preencha esse arquivo; ele tem prioridade aqui.
#define ETE_REDE_EXPERIMENTAL 0  // Altere para 1 somente em bancada.
#define ETE_WIFI_SSID ""          // Nome da rede Wi-Fi.
#define ETE_WIFI_PASSWORD ""      // Senha da rede Wi-Fi.
#define ETE_DEVICE_KEY ""         // Chave DEVICE gerada no sistema.
#define ETE_SERVER_HOST ""        // Host HTTPS, sem https:// ou caminho.
#define ETE_SERVER_PORT 443
#endif
#if ETE_REDE_EXPERIMENTAL
#include "firmware/rede.h"
#endif

// =====================================================
// PINOS
// =====================================================

const byte PINO_TRIG = 4;
const byte PINO_ECHO = 5;
const byte PINO_NTC = A0;
const byte PINO_SERVO = 9;


// =====================================================
// CONFIGURACAO DO SENSOR ULTRASSONICO
// =====================================================

// Distancia do sensor ate o fundo de referencia do canal.
//
// IMPORTANTE:
// Substitua -1.0 pela distancia real em centimetros.
// Exemplo: se a distancia for 50 cm, coloque 50.0.
//
// Enquanto estiver em -1.0, a vazao nao sera calculada
// e o servo sera comandado para a posicao fechada.
const float DISTANCIA_SENSOR_FUNDO_CM = -1.0f;

// Distancia minima permitida entre o sensor e a agua.
const float DISTANCIA_MINIMA_AGUA_CM = 20.0f;

// Ajuste de acordo com o modelo do seu sensor.
const unsigned int DISTANCIA_MAXIMA_SENSOR_CM = 400;


// =====================================================
// CONFIGURACAO DA DOSAGEM
// =====================================================

// Hipotese de teste: 10 mL de mistura por litro de rejeito.
const float DOSE_ML_POR_LITRO = 10.0f;

// Hipotese de teste: vazao com o registro totalmente aberto.
const float VAZAO_MAXIMA_MISTURA_L_S = 4.33f;


// =====================================================
// CONFIGURACAO DO SERVO
// =====================================================

// Ajuste conforme a montagem fisica do seu servo.
const int ANGULO_FECHADO = 0;
const int ANGULO_ABERTO = 90;

// false: servo permanece comandado para fechado.
// true: permite a movimentacao automatica para teste.
const bool HABILITAR_SERVO_TESTE = false;
// Flash e modem podem bloquear o loop; nao permitir controle automatico ate
// demonstrar um limite seguro em hardware e implementar a protecao necessaria.
static_assert(!HABILITAR_SERVO_TESTE, "Controle automatico indisponivel: operacoes bloqueantes");


// =====================================================
// CONFIGURACAO DO NTC 10K
// =====================================================

// Ligacao esperada:
// 5V -> resistor fixo de 10k -> A0 -> NTC 10k -> GND

const float RESISTOR_FIXO_OHMS = 10000.0f;
const float NTC_RESISTENCIA_25C_OHMS = 10000.0f;

// Confirme esse valor na especificacao do seu NTC.
const float NTC_BETA = 3950.0f;

const float TEMPERATURA_REFERENCIA_K = 298.15f;


// =====================================================
// OBJETOS
// =====================================================

Servo servoRegistro;
FlashFila memoriaFila;
Fila<FlashFila> *fila = nullptr;
uint32_t inicioAtual = 0;
#if ETE_REDE_EXPERIMENTAL
Rede *rede = nullptr;
#endif

NewPing sensorUltrassonico(
  PINO_TRIG,
  PINO_ECHO,
  DISTANCIA_MAXIMA_SENSOR_CM
);


// =====================================================
// FECHAR REGISTRO
// =====================================================

void fecharRegistro() {
  servoRegistro.write(ANGULO_FECHADO);
}


// =====================================================
// LEITURA DO SENSOR ULTRASSONICO
// =====================================================

bool lerDistanciaCm(float &distanciaCm) {

  unsigned int tempoEco = sensorUltrassonico.ping();

  // Zero indica que o sensor nao recebeu eco valido.
  if (tempoEco == 0) {
    return false;
  }

  distanciaCm = (float)tempoEco / US_ROUNDTRIP_CM;

  if (distanciaCm < DISTANCIA_MINIMA_AGUA_CM ||
      distanciaCm > DISTANCIA_MAXIMA_SENSOR_CM) {
    return false;
  }

  return true;
}


// =====================================================
// CALCULAR ALTURA DA LAMINA
// =====================================================

bool calcularAlturaCm(float distanciaCm, float &alturaCm) {

  if (DISTANCIA_SENSOR_FUNDO_CM <= 0.0f) {
    return false;
  }

  // A superficie nao deve estar abaixo do fundo definido.
  if (distanciaCm > DISTANCIA_SENSOR_FUNDO_CM) {
    return false;
  }

  alturaCm = DISTANCIA_SENSOR_FUNDO_CM - distanciaCm;

  return true;
}


// =====================================================
// CALCULAR VAZAO DO REJEITO EM m3/h
// =====================================================

float calcularVazaoM3h(float alturaCm) {

  float vazao =
    0.1269f * alturaCm * alturaCm
    + 0.9276f * alturaCm
    - 2.2091f;

  if (vazao < 0.0f) {
    vazao = 0.0f;
  }

  return roundf(vazao * 100.0f) / 100.0f;
}


// =====================================================
// LER TEMPERATURA DO NTC
// =====================================================

bool lerTemperaturaC(float &temperaturaC) {

  int leituraADC = analogRead(PINO_NTC);

  // Evita divisao por zero e leituras extremas.
  if (leituraADC <= 0 || leituraADC >= 1023) {
    return false;
  }

  float resistenciaNTC =
    RESISTOR_FIXO_OHMS
    * ((float)leituraADC / (1023.0f - leituraADC));

  if (resistenciaNTC <= 0.0f) {
    return false;
  }

  float inversoTemperatura =
    (1.0f / TEMPERATURA_REFERENCIA_K)
    + (1.0f / NTC_BETA)
    * logf(resistenciaNTC / NTC_RESISTENCIA_25C_OHMS);

  if (inversoTemperatura <= 0.0f) {
    return false;
  }

  temperaturaC = (1.0f / inversoTemperatura) - 273.15f;

  return isfinite(temperaturaC);
}


// =====================================================
// CONFIGURACAO INICIAL
// =====================================================

void setup() {

  Serial.begin(9600);

  pinMode(PINO_NTC, INPUT);

  servoRegistro.attach(PINO_SERVO);
  fecharRegistro();
  if (memoriaFila.begin()) {
    fila = new Fila<FlashFila>(memoriaFila);
    inicioAtual = uint32_t(fila->maiorSequencia() + 1);
    Serial.print("Capacidade da fila: ");
    Serial.println(fila->capacidade());
#if ETE_REDE_EXPERIMENTAL
    if (!HABILITAR_SERVO_TESTE) rede = new Rede(*fila, inicioAtual);
    else Serial.println("ERRO: rede experimental exige servo automatico desabilitado");
#endif
  } else {
    Serial.println("ERRO: memoria da fila indisponivel ou corrompida");
  }

  Serial.println();
  Serial.println("==================================");
  Serial.println(" PROTOTIPO DE AUTOMACAO DA ETE");
  Serial.println("==================================");
  Serial.println("Atualizacao das leituras: 1 segundo");
  Serial.println("Sensor de pH: nao implementado");

  if (!HABILITAR_SERVO_TESTE) {
    Serial.println("Servo automatico: DESABILITADO");
  }

  if (DISTANCIA_SENSOR_FUNDO_CM <= 0.0f) {
    Serial.println("Configure a distancia ate o fundo.");
  }
}


// =====================================================
// LOOP PRINCIPAL
// =====================================================

void loop() {
  static unsigned long ultimaLeitura = 0;
  static unsigned long ultimaColeta = 0;
  static bool primeiraLeitura = true;
  unsigned long agora = millis();
#if ETE_REDE_EXPERIMENTAL
  static unsigned long ultimaRede = 0;
  if (rede && (unsigned long)(agora - ultimaRede) >= 100UL) {
    ultimaRede = agora;
    fecharRegistro();
    rede->tick(agora);
    agora = millis();
  }
#endif
  if (!primeiraLeitura && (unsigned long)(agora - ultimaLeitura) < 1000UL) {
    return;
  }
  primeiraLeitura = false;
  ultimaLeitura = agora;

  float temperaturaC = 0.0f;
  float distanciaCm = 0.0f;
  float alturaCm = 0.0f;

  bool temperaturaValida = lerTemperaturaC(temperaturaC);
  bool distanciaValida = lerDistanciaCm(distanciaCm);

  // A simulacao local e independente de rede e de falhas individuais dos sensores.
  if ((unsigned long)(agora - ultimaColeta) >= 60000UL) {
    ultimaColeta = agora;
    float alturaColeta = 0.0f;
    bool alturaValida = distanciaValida && calcularAlturaCm(distanciaCm, alturaColeta);
    MedicaoSimulada amostra = montarMedicao(temperaturaValida, temperaturaC,
                                          alturaValida, alturaColeta);
    RegistroFila registro = {};
    registro.inicio = inicioAtual;
    registro.instanteMs = agora;
    if (amostra.temHeightCm) {
      registro.flags |= FILA_ALTURA;
      registro.alturaCenti = int16_t(roundf(amostra.heightCm * 100));
    }
    if (amostra.temTreatedPh) {
      registro.flags |= FILA_PH;
      registro.phCenti = int16_t(roundf(amostra.treatedPh * 100));
    }
    // A gravacao pode bloquear: mantenha o registro comandado fechado.
    fecharRegistro();
    if (!fila || !fila->inserir(registro)) {
      Serial.println("ERRO: fila cheia ou falha de gravacao; amostra nao salva");
    }
    Serial.print("Coleta local - pH entrada: ");
    Serial.print(amostra.rawPh, 2);
    Serial.print("; pH saida: ");
    if (amostra.temTreatedPh) Serial.print(amostra.treatedPh, 2);
    else Serial.print("indisponivel");
    Serial.print("; altura: ");
    if (amostra.temHeightCm) Serial.println(amostra.heightCm, 2);
    else Serial.println("indisponivel");
  }

  Serial.println();
  Serial.println("----------- LEITURAS -----------");

  // Exibe a temperatura.
  Serial.print("Temperatura: ");

  if (temperaturaValida) {
    Serial.print(temperaturaC, 1);
    Serial.println(" C");
  } else {
    Serial.println("leitura invalida");
  }

  // Ainda nao existe um sensor de pH no codigo.
  Serial.println("pH: sensor nao configurado");

  // Exibe a distancia.
  Serial.print("Distancia sensor-agua: ");

  if (distanciaValida) {
    Serial.print(distanciaCm, 2);
    Serial.println(" cm");
  } else {
    Serial.println("leitura invalida ou fora da faixa");
  }

  // Se a distancia for invalida, nao calcula a vazao.
  if (!distanciaValida) {

    fecharRegistro();

    Serial.println("Vazao: nao calculada");
    Serial.println("Servo: comandado para fechado");

    return;
  }

  // Verifica se a distancia ate o fundo foi configurada.
  if (DISTANCIA_SENSOR_FUNDO_CM <= 0.0f) {

    fecharRegistro();

    Serial.println("Altura da lamina: nao calculada");
    Serial.println("Vazao: nao calculada");
    Serial.println("Configure DISTANCIA_SENSOR_FUNDO_CM.");
    Serial.println("Servo: comandado para fechado");

    return;
  }

  // Calcula a altura da lamina.
  if (!calcularAlturaCm(distanciaCm, alturaCm)) {

    fecharRegistro();

    Serial.println("Altura da lamina: leitura invalida");
    Serial.println("Vazao: nao calculada");
    Serial.println("Servo: comandado para fechado");

    return;
  }

  // Vazao da agua de rejeito.
  float vazaoM3h = calcularVazaoM3h(alturaCm);

  // Conversao de m3/h para L/s.
  float vazaoRejeitoLs = vazaoM3h / 3.6f;

  // Calcula a vazao de mistura necessaria em L/s.
  float vazaoMisturaLs =
    vazaoRejeitoLs * DOSE_ML_POR_LITRO / 1000.0f;

  // Calcula a abertura teorica, supondo relacao linear.
  float aberturaSolicitada =
    (vazaoMisturaLs / VAZAO_MAXIMA_MISTURA_L_S) * 100.0f;

  bool capacidadeInsuficiente = aberturaSolicitada > 100.0f;

  // Limita a abertura comandada entre 0% e 100%.
  float aberturaComandada =
    constrain(aberturaSolicitada, 0.0f, 100.0f);

  // Converte o percentual para o angulo do servo.
  float anguloCalculado =
    ANGULO_FECHADO
    + (ANGULO_ABERTO - ANGULO_FECHADO)
      * aberturaComandada / 100.0f;

  int anguloServo = (int)roundf(anguloCalculado);

  if (HABILITAR_SERVO_TESTE) {
    servoRegistro.write(anguloServo);
  } else {
    fecharRegistro();
  }

  // Exibe os resultados.
  Serial.print("Altura da lamina: ");
  Serial.print(alturaCm, 2);
  Serial.println(" cm");

  Serial.print("Vazao do rejeito: ");
  Serial.print(vazaoM3h, 2);
  Serial.println(" m3/h");

  Serial.print("Vazao do rejeito: ");
  Serial.print(vazaoRejeitoLs, 3);
  Serial.println(" L/s");

  Serial.print("Mistura necessaria: ");
  Serial.print(vazaoMisturaLs * 1000.0f, 1);
  Serial.println(" mL/s");

  Serial.print("Abertura teorica solicitada: ");
  Serial.print(aberturaSolicitada, 2);
  Serial.println(" %");

  Serial.print("Angulo teorico: ");
  Serial.print(anguloCalculado, 2);
  Serial.println(" graus");

  if (capacidadeInsuficiente) {
    Serial.println("ALERTA: vazao solicitada acima da capacidade.");
  }

  if (HABILITAR_SERVO_TESTE) {
    Serial.print("Angulo enviado ao servo: ");
    Serial.print(anguloServo);
    Serial.println(" graus");
  } else {
    Serial.println("Servo automatico desabilitado: fechado");
  }

}
