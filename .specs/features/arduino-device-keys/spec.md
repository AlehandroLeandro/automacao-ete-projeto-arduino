# Integração Arduino (chaves de dispositivo) Specification

## Problem Statement

A coleta de vazão e pH é manual. O projeto passa a coletar dados em tempo real por sensores ligados a Arduinos. Cada Arduino precisa se autenticar sem ter um usuário no sistema, usando uma chave fixa gravada no firmware, e enviar leituras a cada 1 minuto. Para não encher o banco, o sistema consolida as leituras de cada hora em uma média e descarta as leituras de 1 minuto. O ADMIN gerencia as chaves por uma tela nova.

## Goals

- [ ] ADMIN gera, nomeia, vê, copia, desativa, reativa e exclui chaves, cada chave vinculada a uma ETE.
- [ ] Um Arduino com chave ativa faz login, recebe JWT e envia vazão e pH (entrada/saída) em um único POST.
- [ ] Ao fim de cada hora, as leituras viram um único registro de média por chave e tipo nas tabelas atuais, e as leituras de 1 minuto são apagadas.
- [ ] O dashboard mostra o último valor recebido (1 minuto); relatórios, listas, gráficos e médias do dia usam só as médias horárias.
- [ ] ADMIN, SUPERVISOR e operadores da ETE são notificados quando um Arduino ativo fica mais de 5 minutos sem enviar.

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
| ------- | ------ |
| Editar nome ou ETE de uma chave existente | Decisão do usuário: excluir e gerar outra |
| Buffer/backlog em lote no Arduino (envio de várias leituras atrasadas em uma requisição) | Fora do pedido; o firmware envia 1 leitura por minuto |
| Mostrar o valor da última leitura na lista da tela Automação | Decisão do usuário: a lista mostra só último envio e selo online/offline |
| Auditar ver/copiar chave | Decisão do usuário: não precisa de auditoria |
| Refresh token para o Arduino | Decisão do usuário: em 401 o Arduino refaz o login com a chave |
| Rotas de leitura, edição ou exclusão para o papel DEVICE | O Arduino só escreve em `/api/device/readings` |
| Terminação TLS no backend | HTTPS é assumido via proxy reverso |

---

## Assumptions & Open Questions

Every ambiguity is resolved or recorded here - nothing is left silently unclear.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Cada chave pertence a uma ETE; nome único por ETE sem diferenciar maiúsculas | Várias chaves por ETE; qualquer chave envia vazão e pH | Grilling Q1/Q13 | y |
| Autoria do registro de média | `created_by` fica opcional; registro guarda `device_key_id` (nulo ao excluir a chave) e `device_name` (cópia do nome) | Grilling Q2/Q18; "Equipamento: nome" continua após a exclusão | y |
| Armazenamento da chave | AES-GCM com `DEVICE_KEY_ENCRYPTION_SECRET` (env obrigatória) + hash SHA-256 para o login | Grilling Q3/Q10; o ADMIN precisa rever a chave e o login não pode depender da decifragem | y |
| Caracteres da chave | 24 caracteres; maiúscula, minúscula, número e símbolo de `!@#$%^&*-_=+?`; `SecureRandom` | Grilling Q8; evita aspas e barra invertida em C/JSON | y |
| Validade do access token do Arduino | Mesma propriedade `JWT_ACCESS_TOKEN_MINUTES` dos usuários | Reaproveita configuração; em 401 o Arduino refaz o login | n |
| Resposta do login quando o IP está bloqueado | HTTP 429 sem corpo informativo; falhas de chave retornam 401 genérico | O 429 não revela se a chave existe; Grilling Q16 pediu 401 genérico para falhas | n |
| Limitador do login | 10 falhas em 5 minutos bloqueiam o IP por 15 minutos, em memória, só em `/api/auth/device-login` | Grilling Q16 | y |
| Limite de envio | 10 requisições por minuto por chave; acima, HTTP 429 e nada é gravado | Grilling Q23 | y |
| HTTPS | Assumido via proxy reverso; hardware do Arduino ainda não definido | Grilling Q9; risco registrado no design | y |
| Janela da hora | Leituras em `[HH:00, HH+1:00)` no fuso America/Sao_Paulo; `measuredAt` da média = fim da janela | Grilling Q19 (leituras 14:00–14:59 viram 15:00) | y |
| Hora sem leituras | Não gera registro | Grilling Q19 | y |
| Média de vazão | `heightCm` = média das alturas; `flowLiters` = média dos litros de cada leitura; escala 2, arredondamento HALF_UP | Grilling Q19; a conta altura→litros pode não ser linear | y |
| Média de pH | Média aritmética, escala 2, HALF_UP; pH entrada = `RAW`, pH saída = `TREATED` | Grilling Q19; `PhWaterType` já existe | y |
| Quando a rotina roda | Todo início de hora (HH:00:30) com catch-up idempotente de todas as horas fechadas pendentes | Grilling Q20 | y |
| Identificação do registro de uma hora | Coluna imutável `device_window_end` (fim da janela) nos registros de média, com índice único por chave e tipo; a edição de `measuredAt` pelo operador não altera essa coluna | Evita duplicar a média se o operador mudar o horário do registro; garante idempotência da rotina | y |
| Hora reaberta por leitura atrasada | Só quando o registro da hora já existe; média ponderada `(média×n + x)/(n+1)` e `sample_count + 1`; leitura sem registro da hora fica na tabela bruta e entra na próxima rotina | Grilling Q20/Q25; as leituras brutas já foram apagadas | y |
| Hora editada ou excluída por humano | Leitura atrasada é descartada (200, ignorada, log). "Editada" = existe evento de auditoria UPDATED do registro; "excluída" = `deleted = true` | Grilling Q25; a correção humana vale mais | y |
| Limite para reabrir hora | 24 horas | Grilling Q25; alinhado à janela do operador | y |
| `measuredAt` do Arduino | Opcional; sem ele vale a hora do servidor; não pode ser futuro | Grilling Q6/Q25; Arduino pode não ter relógio | y |
| Validação do POST | Valores válidos são aceitos e inválidos ignorados (200 com resposta listando aceitos e rejeitados); sem valor válido, HTTP 400 | Grilling Q27 | y |
| Excluir chave | Antes, agrega as leituras brutas pendentes dela (hora parcial) e só então apaga; remove valores ao vivo e zera `device_key_id` nos registros | Grilling Q18/Q26 | y |
| Dashboard ao vivo | Valor mais recente por horário entre o último registro nas tabelas e a última leitura ao vivo; várias chaves na ETE: a mais recente por tipo | Grilling Q26 | y |
| Edição de registros do Arduino | ADMIN e OPERATOR editam e excluem as médias horárias pelas regras atuais (janela do operador, descrição de auditoria, trilha de auditoria); OPERATOR vê registros de Arduino da própria ETE | Grilling Q14 | y |
| Auditoria do ciclo de vida | Criar, desativar, reativar e excluir chave geram evento de auditoria `DEVICE_KEY` com descrição fixa; nunca contém o valor da chave | Grilling Q11/Q24 | y |
| Alerta sem sinal | Chave ativa que já enviou ao menos 1 leitura e está >5 min sem enviar (`DEVICE_OFFLINE_MINUTES`, padrão 5); notifica ADMIN, SUPERVISOR e OPERATORs da ETE; 1 alerta por queda; aviso quando voltar | Grilling Q22 | y |
| Lembretes de aferição às hh:55 e medições manuais | Continuam como hoje; uma hora pode ter registro manual e média do Arduino | Grilling Q21 | y |
| Selo online/offline | Online quando o último envio tem até 5 minutos; offline acima disso; sem envio ainda: "Sem sinal ainda" | Mesma regra do alerta | n |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Administrador gerencia chaves de Arduino ⭐ MVP

**User Story**: As ADMIN, I want to generate, name, view, copy, disable, re-enable and delete device keys linked to a station so that I know which equipment is collecting data and can control its access.

**Why P1**: Sem chave não há integração; é a porta de entrada de todo o resto.

**Acceptance Criteria** (each line is one EARS pattern):

1. WHEN o ADMIN informa um nome e uma ETE e confirma THEN the system SHALL criar a chave com valor aleatório de 24 caracteres contendo ao menos uma maiúscula, uma minúscula, um número e um símbolo de `!@#$%^&*-_=+?`, ativa e vinculada à ETE.
2. IF já existe chave com o mesmo nome (sem diferenciar maiúsculas) na mesma ETE THEN the system SHALL rejeitar a criação com HTTP 409.
3. The system SHALL guardar o valor da chave cifrado com AES-GCM e guardar também seu hash SHA-256, e nunca em texto puro no banco.
4. The system SHALL devolver na listagem apenas id, nome, ETE, estado (ativa/desativada), data de criação e último envio, sem o valor da chave.
5. WHEN o ADMIN aciona "ver" THEN the system SHALL buscar o valor em `GET /api/device-keys/{id}/secret` e a tela SHALL alternar entre exibir e ocultar o valor, ocultando-o sozinha após 15 segundos.
6. WHEN o ADMIN aciona "copiar" THEN the system SHALL copiar o valor para a área de transferência sem exibi-lo na tela.
7. IF o usuário autenticado não é ADMIN THEN the system SHALL responder HTTP 403 em todas as rotas `/api/device-keys/**` e a tela SHALL ocultar o botão "Automação" e bloquear a rota `/app/automacao`.
8. WHEN o ADMIN confirma desativar uma chave THEN the system SHALL marcá-la como desativada e o Arduino SHALL ser rejeitado com HTTP 401 já na requisição seguinte, mesmo com JWT ainda válido.
9. WHEN o ADMIN confirma reativar uma chave desativada THEN the system SHALL marcá-la como ativa.
10. WHEN o ADMIN digita exatamente `EXCLUIR` na confirmação de exclusão THEN the system SHALL agregar as leituras brutas pendentes da chave, apagar a chave e seus valores ao vivo e manter os registros de média com `device_key_id` nulo e `device_name` preservado.
11. IF o texto digitado na confirmação de exclusão é diferente de `EXCLUIR` THEN the system SHALL manter o botão de confirmar desabilitado e SHALL NOT apagar a chave.
12. WHEN o ADMIN cria, desativa, reativa ou exclui uma chave THEN the system SHALL registrar um evento de auditoria `DEVICE_KEY` com o usuário, a ETE e a ação, sem o valor da chave.
13. WHEN o ADMIN abre a aba Mais THEN the system SHALL exibir o botão "Automação" ao lado de Relatórios, Vazão e demais botões.

**Independent Test**: Logar como ADMIN, criar chave "Sensor Entrada" na ETE A, ver, copiar, desativar, reativar e excluir digitando EXCLUIR; conferir cada resposta e o histórico de auditoria.

---

### P1: Arduino autentica e envia leituras ⭐ MVP

**User Story**: As an Arduino device, I want to log in with my key and post my sensor readings so that the system receives data in real time without a user account.

**Why P1**: É o fluxo de dados que justifica a feature.

**Acceptance Criteria**:

1. WHEN o Arduino envia uma chave ativa válida a `POST /api/auth/device-login` THEN the system SHALL responder 200 com um access token JWT de papel `DEVICE` contendo o id da chave e a ETE, sem refresh token.
2. IF a chave é desconhecida, desativada ou malformada THEN the system SHALL responder HTTP 401 com a mesma resposta genérica.
3. IF um IP acumula 10 falhas de `device-login` em 5 minutos THEN the system SHALL responder HTTP 429 a esse IP por 15 minutos.
4. WHILE o JWT é de papel `DEVICE` the system SHALL permitir somente `POST /api/device/readings` e responder HTTP 403 em qualquer outra rota protegida.
5. WHEN o Arduino envia `POST /api/device/readings` com `heightCm`, `rawPh` e/ou `treatedPh` THEN the system SHALL guardar uma leitura bruta na ETE da chave para cada valor enviado e nenhuma para os não enviados.
6. IF o corpo não traz nenhum dos três valores THEN the system SHALL responder HTTP 400.
7. IF um valor está fora da faixa (`heightCm` fora de 0–45, pH fora de 0–14) ou com mais de 2 casas THEN the system SHALL ignorar esse valor, aceitar os válidos e responder 200 listando campos aceitos e rejeitados.
8. IF nenhum valor enviado é válido THEN the system SHALL responder HTTP 400 sem gravar nada.
9. WHEN `measuredAt` não é enviado THEN the system SHALL usar a hora do servidor.
10. IF `measuredAt` é maior que a hora atual THEN the system SHALL responder HTTP 400.
11. IF a chave excede 10 requisições em 1 minuto THEN the system SHALL responder HTTP 429 e SHALL NOT gravar a requisição.
12. WHEN uma leitura é aceita THEN the system SHALL atualizar o último envio da chave e os últimos valores ao vivo (altura/vazão, pH entrada, pH saída) com o horário da leitura.
13. The system SHALL calcular `flowLiters` de cada leitura de vazão com `FlowRateCalculator` a partir de `heightCm`.

**Independent Test**: Criar chave, fazer device-login, enviar `{heightCm, rawPh, treatedPh}`, conferir 200 e 3 leituras brutas; repetir com valores inválidos, corpo vazio e 11 envios em 1 minuto.

---

### P1: Consolidação por hora ⭐ MVP

**User Story**: As the system operator, I want each hour of readings consolidated into one average record so that the database stays small and reports keep one record per hour.

**Why P1**: É a regra que evita sobrecarregar o servidor.

**Acceptance Criteria**:

1. WHEN a rotina horária roda THEN the system SHALL agregar, por chave e tipo (vazão, pH entrada, pH saída), todas as leituras brutas de horas já fechadas em America/Sao_Paulo e gravar um registro de média por grupo.
2. The system SHALL gravar o registro de média com `measuredAt` igual ao fim da janela da hora (leituras de 14:00 a 14:59 geram registro às 15:00), `created_by` nulo, `device_key_id`, `device_name` e `sample_count` iguais ao número de leituras.
3. The system SHALL calcular `flowLiters` do registro como a média dos litros de cada leitura e `heightCm` como a média das alturas, ambos com 2 casas e HALF_UP.
4. The system SHALL calcular o pH do registro como a média aritmética com 2 casas e HALF_UP, com `RAW` para pH entrada e `TREATED` para pH saída.
5. WHEN um grupo é agregado THEN the system SHALL apagar fisicamente as leituras brutas do grupo na mesma transação que grava o registro de média.
6. IF um grupo (chave, tipo, hora) não tem leituras THEN the system SHALL NOT gravar registro.
7. IF o servidor ficou parado e várias horas fecharam sem agregação THEN the system SHALL agregar todas na próxima execução e SHALL NOT duplicar registros já gravados.
8. WHEN chega uma leitura com `measuredAt` em uma hora fechada cujo registro de média existe, não foi editado nem excluído e tem até 24 horas THEN the system SHALL recalcular a média por média ponderada, incrementar `sample_count` e manter `measuredAt` do registro.
9. IF o registro da hora foi editado (existe evento de auditoria UPDATED) ou foi excluído, ou IF a hora da leitura fechou há mais de 24 horas THEN the system SHALL descartar a leitura atrasada com resposta 200 e log, sem alterar nem criar registro.
10. WHEN chega uma leitura de hora fechada há até 24 horas que ainda não tem registro de média THEN the system SHALL guardá-la como leitura bruta para a próxima rotina.
11. WHEN uma chave é excluída THEN the system SHALL agregar as leituras brutas pendentes dela, incluindo a hora parcial, gravando a média da hora parcial com `measuredAt` igual ao instante da exclusão, e só então apagá-las.

**Independent Test**: Inserir 60 leituras brutas de 14:00 a 14:59 e rodar a rotina às 15:00:30; esperar 1 registro às 15:00 com `sample_count` 60, média correta e nenhuma leitura bruta restante.

---

### P1: Registros do Arduino no sistema ⭐ MVP

**User Story**: As an operator or admin, I want the hourly averages to appear in lists, reports and dashboard like manual records, labelled by equipment, so that I can use and correct them.

**Why P1**: Sem isso os dados coletados não aparecem para ninguém.

**Acceptance Criteria**:

1. The system SHALL permitir `created_by` nulo nos registros de vazão e pH, e as respostas SHALL trazer o nome do equipamento no lugar do autor.
2. WHEN a UI exibe um registro sem autor humano THEN the system SHALL mostrar "Equipamento: <nome da chave>" no campo de autoria.
3. WHILE o usuário é OPERATOR the system SHALL listar e detalhar os registros de Arduino da própria ETE, além dos que ele criou.
4. WHEN um OPERATOR ou ADMIN edita ou exclui uma média horária dentro das regras atuais (janela do operador e descrição de auditoria) THEN the system SHALL aplicar a edição ou exclusão e registrar a auditoria.
5. IF um OPERATOR de outra ETE tenta ler ou alterar um registro de Arduino THEN the system SHALL responder como a regra atual de estação, sem revelar o registro.
6. The system SHALL usar somente registros de média horária em listas, relatórios, gráficos, médias do dia e totais.
7. The system SHALL NOT exibir leituras brutas de 1 minuto em listas nem relatórios.

**Independent Test**: Com um registro de média criado pela rotina, logar como OPERATOR da ETE e ver na lista de vazão "Equipamento: <nome>"; editar com descrição; logar como OPERATOR de outra ETE e não ver o registro.

---

### P2: Valores ao vivo no dashboard

**User Story**: As an admin, supervisor or operator, I want the dashboard to show the latest value that arrived from the equipment so that I see the plant state without waiting for the hourly average.

**Why P2**: Valor operacional alto, mas o sistema funciona sem ele.

**Acceptance Criteria**:

1. WHEN o dashboard monta `latestFlow`, `latestRawPh` e `latestTreatedPh` THEN the system SHALL escolher, por horário, o mais recente entre o último registro das tabelas e a última leitura ao vivo das chaves da ETE.
2. WHERE a ETE tem várias chaves the system SHALL usar a leitura ao vivo mais recente por tipo.
3. The system SHALL exibir com cada valor ao vivo o horário da leitura, como os cards fazem hoje.
4. WHEN uma chave é excluída THEN the system SHALL deixar de usar seus valores ao vivo e voltar ao último registro das tabelas.
5. The system SHALL NOT usar valores ao vivo em séries, médias do dia, totais ou relatórios.

**Independent Test**: Enviar uma leitura nova mais recente que o último registro manual e ver o card mostrar o valor e a hora dela; criar um registro manual mais novo e ver o card trocar.

---

### P2: Alerta de Arduino sem sinal

**User Story**: As an admin, supervisor or operator of the station, I want to be notified when an active Arduino stops sending so that someone checks the equipment.

**Why P2**: Evita perder coleta sem perceber.

**Acceptance Criteria**:

1. WHEN uma chave ativa que já enviou ao menos uma leitura fica mais de `DEVICE_OFFLINE_MINUTES` (padrão 5) sem enviar THEN the system SHALL notificar os usuários ativos ADMIN, SUPERVISOR e OPERATOR da mesma ETE com tipo `DEVICE_OFFLINE_ALERT`, nome da chave e ETE.
2. The system SHALL enviar apenas 1 alerta por queda, com chave de deduplicação formada pelo id da chave e o horário do último envio.
3. WHEN uma chave que estava sem sinal volta a enviar THEN the system SHALL notificar os mesmos destinatários que o equipamento voltou.
4. IF a chave está desativada ou nunca enviou leitura THEN the system SHALL NOT enviar alerta.
5. WHEN a tela Automação lista as chaves THEN the system SHALL mostrar o último envio e um selo online (até 5 minutos), offline (acima de 5 minutos) ou "Sem sinal ainda" (nunca enviou).

**Independent Test**: Com chave ativa e último envio 6 minutos atrás, rodar a verificação e ver 1 alerta aos destinatários; rodar de novo e não ver outro; enviar leitura e ver o aviso de volta.

---

## Edge Cases

- IF a requisição de `device-login` traz corpo sem `key` ou com `key` diferente de 24 caracteres THEN the system SHALL responder HTTP 401 genérico.
- IF `DEVICE_KEY_ENCRYPTION_SECRET` está ausente ou é um placeholder conhecido THEN the system SHALL falhar na inicialização com mensagem clara.
- IF a decifragem de uma chave falha em `GET /api/device-keys/{id}/secret` THEN the system SHALL responder HTTP 500 sem expor detalhes, e o login dessa chave SHALL continuar funcionando pelo hash.
- WHEN a rotina horária falha em um grupo THEN the system SHALL registrar log do erro, manter as leituras brutas desse grupo e continuar com os demais grupos.
- WHEN uma leitura chega exatamente no limite de uma hora (por exemplo, 15:00:00) THEN the system SHALL colocá-la na janela que começa em 15:00.
- IF um mesmo `measuredAt` chega duas vezes na mesma chave e tipo THEN the system SHALL tratar como duas leituras (sem deduplicação).
- WHEN o operador lança medição manual na mesma hora de uma média do Arduino THEN the system SHALL manter os dois registros.
- IF o JWT de um DEVICE é de uma chave excluída THEN the system SHALL responder HTTP 401.

---

## Requirement Traceability

Each requirement gets a unique ID for tracking across design, tasks, and validation.

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| DEV-01 | P1: Administrador gerencia chaves (geração, unicidade, ETE) | Tasks | In Tasks |
| DEV-02 | P1: Administrador gerencia chaves (armazenamento cifrado, listagem sem valor, secret) | Tasks | In Tasks |
| DEV-03 | P1: Administrador gerencia chaves (tela, acesso ADMIN, ver/copiar, botão em Mais) | Tasks | In Tasks |
| DEV-04 | P1: Administrador gerencia chaves (desativar, reativar, excluir com EXCLUIR) | Tasks | In Tasks |
| DEV-05 | P1: Administrador gerencia chaves (auditoria DEVICE_KEY) | Tasks | In Tasks |
| DEV-06 | P1: Arduino autentica (device-login, JWT DEVICE, limitador) | Tasks | In Tasks |
| DEV-07 | P1: Arduino autentica (restrição do papel DEVICE, chave ativa a cada requisição) | Tasks | In Tasks |
| DEV-08 | P1: Arduino envia leituras (POST, validação, limite, últimos valores) | Tasks | In Tasks |
| DEV-09 | P1: Consolidação por hora (agregação, catch-up, exclusão de brutas) | Tasks | In Tasks |
| DEV-10 | P1: Consolidação por hora (reabertura de hora e descartes) | Tasks | In Tasks |
| DEV-11 | P1: Registros do Arduino (autoria opcional, visibilidade, edição) | Tasks | In Tasks |
| DEV-12 | P2: Valores ao vivo no dashboard | Tasks | In Tasks |
| DEV-13 | P2: Alerta de Arduino sem sinal e selo online/offline | Tasks | In Tasks |
| DEV-14 | P1: Registros do Arduino (somente médias em listas, relatórios e totais) | Tasks | In Tasks |

**ID format:** `[CATEGORY]-[NUMBER]` (e.g., `DEV-01`)

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 14 total, 14 mapped to tasks, 0 unmapped

---

## Success Criteria

How we know the feature is successful:

- [ ] Um Arduino de teste (ou `curl`) faz login e envia 60 leituras em 1 hora; o banco termina a hora com 1 registro por tipo e 0 leituras brutas dessa hora.
- [ ] Desativar uma chave faz o Arduino receber 401 na próxima requisição.
- [ ] Nenhuma rota fora de `/api/device/readings` aceita o JWT de um DEVICE.
- [ ] O valor de uma chave nunca aparece em listagem, log ou evento de auditoria.
- [ ] Relatórios e totais do dia de vazão ficam com no máximo 1 registro por hora por chave.
