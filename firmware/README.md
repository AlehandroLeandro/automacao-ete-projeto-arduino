# Firmware do protótipo — integração experimental

O sketch `arduino.c++` mantém as leituras e o fechamento local, registra a cada minuto pH de entrada 2 e pH de saída pela temperatura dividida por 2,5 (limitado a 0–14 e arredondado a duas casas). NTC inválido omite somente o pH de saída; altura inválida ou fora de 0–45 cm é omitida. Configure `DISTANCIA_SENSOR_FUNDO_CM` para obter altura. O primeiro registro ocorre após um minuto ligado.

**A issue #19 permanece incompleta:** a rede usa WiFiS3, cujas operações do modem podem bloquear o loop, e gravações de flash também são bloqueantes. Assim não foi comprovado o controle e a leitura a cada segundo durante falhas de rede. Por segurança, a rede experimental é desabilitada por padrão e uma verificação de compilação impede ativar `HABILITAR_SERVO_TESTE` enquanto existir esse risco, mesmo com rede desabilitada. O servo recebe comando de fechamento, mas seu fechamento físico não foi verificado. O firmware não deve ser usado para controlar um processo real. Não houve testes físicos nesta placa.

## Configuração e compilação

No início de `arduino.c++`, preencha `ETE_WIFI_SSID` com o nome da rede, `ETE_WIFI_PASSWORD` com a senha e `ETE_DEVICE_KEY` com a chave DEVICE gerada no sistema. Preencha também `ETE_SERVER_HOST` com o hostname HTTPS do servidor e ajuste `ETE_SERVER_PORT` se necessário. Para manter as credenciais fora do sketch, copie `firmware/config.example.h` para `firmware/config.h` (ignorado pelo Git) e preencha os mesmos campos nesse arquivo: ele tem prioridade sobre os valores do sketch. Não compartilhe nem versione credenciais reais. A implementação experimental usa TLS, não aceita URL completa no campo hostname e ainda precisa ser validada com o certificado e servidor reais. Configure `ETE_REDE_EXPERIMENTAL` para 1 somente em bancada. Login, renovação em 401 e lote de uma amostra por requisição foram implementados; falhas ou ausência de confirmação individual preservam a amostra. IDs são sequências decimais por dispositivo. Uma confirmação completa é necessária para remover um item. A comunicação e o formato HTTP ainda não foram testados contra um servidor real.

Para compilar, copie `arduino.c++` como arquivo `.ino` para um diretório de mesmo nome e copie `firmware/` para esse diretório. Instale o pacote Arduino UNO R4 Boards e as bibliotecas Servo, NewPing e ArduinoJson 7; selecione UNO R4 WiFi. A compilação local usou core `arduino:renesas_uno` 1.6.0, Servo 1.3.0, NewPing 1.9.7 e ArduinoJson 7.4.3. O NewPing emite aviso de arquitetura não declarada; sua operação real deve ser verificada na placa.

## Fila, memória e horário

A [documentação da placa](https://docs.arduino.cc/resources/datasheets/ABX00087-datasheet.pdf) informa 8 KiB de data flash. O formato implementado usa quatro pares de blocos de 1 KiB; cada par contém cópias alternadas verificadas por CRC, com 42 registros de 24 bytes. A capacidade **calculada é 168 amostras**, cerca de 2 h 48 min a uma por minuto, não 2.880 amostras ou 48 horas. O firmware recusa memória com cópias inválidas e não apaga EEPROM previamente ocupada automaticamente. A fila preserva as amostras existentes quando cheia e sinaliza falha na serial. O armazenamento reserva toda a data flash e não pode ser compartilhado com outras aplicações de EEPROM.

A simulação cobre uma gravação parcialmente interrompida, mas não prova atomicidade elétrica nem durabilidade em hardware. A [documentação da EEPROM](https://docs.arduino.cc/tutorials/uno-r4-wifi/eeprom/) alerta para o limite de ciclos de escrita; cada inclusão e confirmação regrava um bloco, sem garantia de vida útil. Não há coletas sem energia. Quando `WiFi.getTime()` retorna hora, amostras do período ligado atual são estimadas a partir de `millis()` e marcadas como estimadas; após reinício, horários anteriores não recuperáveis são omitidos e o servidor usa hora de recebimento. O relógio depende da validade da sincronização Wi-Fi e a estimativa não cobre transbordamento de `millis()` nem demonstra precisão física. O servidor pode descartar dados fora da janela de 48 horas.

## Testes locais

```sh
g++ -std=c++11 -Wall -Wextra -Werror firmware/tests/medicoes_test.cpp -o /tmp/ete-medicoes-test && /tmp/ete-medicoes-test
g++ -std=c++11 -Wall -Wextra -Werror firmware/tests/fila_test.cpp -o /tmp/ete-fila-test && /tmp/ete-fila-test
g++ -std=c++11 -Wall -Wextra -Werror -Ifirmware/tests/stubs firmware/tests/flash_fila_test.cpp -o /tmp/ete-flash-test && /tmp/ete-flash-test
g++ -std=c++11 -Wall -Wextra -Werror -I"$HOME/Arduino/libraries/ArduinoJson/src" firmware/tests/confirmacao_test.cpp -o /tmp/ete-confirma-test && /tmp/ete-confirma-test
g++ -std=c++11 -Wall -Wextra -Werror -Ifirmware/tests/stubs -I"$HOME/Arduino/libraries/ArduinoJson/src" firmware/tests/rede_test.cpp -o /tmp/ete-rede-test && /tmp/ete-rede-test
```

Os testes exercitam os limites da simulação, fila cheia, reinício, ordem, confirmação errada ou repetida e uma interrupção simulada de gravação. Com modem e flash simulados, cobrem reconexão, login, 401 seguido de novo login, timeout, resposta duplicada e confirmação ausente ou incompleta; nenhum item é removido até que todos os campos enviados tenham um resultado definitivo. Não cobrem Wi-Fi real, autenticação com servidor real, TLS, flash físico ou temporização real.

## Roteiro físico pendente

1. Com o servo desacoplado do processo, confira alimentação, ligações e posição efetivamente fechada; mantenha `HABILITAR_SERVO_TESTE` desabilitado. Configure a distância até o fundo e confira altura e temperatura na serial.
2. Em bancada, configure a rede experimental e confira login, amostras e certificado TLS. Meça intervalos máximos das leituras locais, inclusive durante reconexão, falha de servidor e escrita de flash.
3. Interrompa a rede e preencha a fila. Reinicie a placa sem rede, retome a conexão e compare IDs e ordem; simule perda da resposta e confirmação parcial. Confirme o sinal de fila cheia.
4. Corte energia durante gravação em diferentes instantes, repita testes de desgaste adequados e confira horários estimados e recebidos, incluindo reinício e dados fora da janela de 48 horas. A aprovação depende dessas medições e de resolver o bloqueio de controle.

A fonte [WiFiS3 `begin`](https://github.com/arduino/ArduinoCore-renesas/blob/main/libraries/WiFiS3/src/WiFi.cpp) aguarda conexão em laço; o timeout configurado nesta versão limita esse laço, mas não demonstra um limite para todos os comandos do modem. Fechar o servo antes da rede não garante controle contínuo.
