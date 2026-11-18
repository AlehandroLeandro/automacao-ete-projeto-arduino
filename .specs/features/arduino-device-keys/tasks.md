# Integração Arduino (chaves de dispositivo) Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: `.specs/features/arduino-device-keys/design.md`
**Status**: Draft

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec - confirm before Execute. Guidelines found: none (`AGENTS.md`, `CONTRIBUTING.md` e `docs/` não existem; `pom.xml` sem limite de cobertura; `frontend/test-runner.mts` roda `tests/**/*.test.{ts,mts,mjs}`) - strong defaults applied. Amostra de testes existentes: `FlowMeasurementServiceTest` (unit/Mockito), `FlowMeasurementControllerTest` (`@WebMvcTest` + `@WithMockUser`), `FlowMeasurementRepositoryTest` (`@DataJpaTest` em H2, perfil `test`), `SecurityConfigTest` (`@WebMvcTest` + `SecurityConfig`), `NotificationSchedulerTest`, `frontend/tests/utils/*.test.ts`.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Service / domínio (`DeviceKeyService`, `DeviceReadingService`, `DeviceHourlyAggregator`, `DeviceAuthService`, `DeviceOfflineMonitor`, `DashboardService`, utilitários de chave, limitadores, policy) | unit | Todos os ramos; 1:1 com os ACs da spec; todo edge case listado tem teste | `backend/src/test/java/br/com/carbonifera/ete/**/*Test.java` | `cd backend && mvn -q test -Dtest=<Classe>Test` |
| Controller / segurança (`DeviceKeyController`, `DeviceAuthController`, `DeviceReadingController`, `SecurityConfig`, filtro JWT) | integration (`@WebMvcTest`) | Todas as rotas da tarefa: caminho feliz + cada edge case + erros (401/403/409/429/400) | `backend/src/test/java/br/com/carbonifera/ete/**/*ControllerTest.java`, `security/*Test.java` | `cd backend && mvn -q test -Dtest=<Classe>Test` |
| Repositório / acesso a dados | integration (`@DataJpaTest`, H2) | Consultas principais + caminhos de erro/vazio | `backend/src/test/java/br/com/carbonifera/ete/**/*RepositoryTest.java` | `cd backend && mvn -q test -Dtest=<Classe>Test` |
| Agendador / monitor (`DeviceHourlyScheduler`, `DeviceOfflineMonitor`) | unit (com `Clock` fixo) | Cada gatilho e cada guarda do AC | `backend/src/test/java/br/com/carbonifera/ete/device/*Test.java` | `cd backend && mvn -q test -Dtest=<Classe>Test` |
| Entidade / configuração / schema (`DeviceKey`, `DeviceReading`, `DeviceProperties`, `schema.sql`, YAML/env) | none | - (gate de build) | - | `cd backend && mvn -q test` |
| Frontend utilitários, adaptadores, permissões, API client | unit (`vite-node`) | Todos os ramos de cada função pura; payloads exatos | `frontend/tests/**/*.test.ts` | `cd frontend && npm test` |
| Frontend página/componente React | none | - (não há biblioteca de teste de componente; lógica isolada em `utils/`; gate = `tsc` no build + verificação visual do Verifier) | - | `cd frontend && npm run build` |
| Documentação / verificação manual em Postgres (`README.md`, T54) | none | - (sem código de produção; T54 é checagem manual contra Postgres real) | - | gate de build |
| Integração ponta a ponta do backend | integration (`@SpringBootTest` + H2 + MockMvc) | Fluxo login → envio → agregação → lista/dashboard e isolamento do papel DEVICE | `backend/src/test/java/br/com/carbonifera/ete/device/*IntegrationTest.java` | `cd backend && mvn -q test -Dtest=<Classe>Test` |

## Gate Check Commands

> Generated from codebase - confirm before Execute. Não há linter/formatador configurado (`pom.xml` sem checkstyle/spotless; frontend sem eslint); `tsc` roda dentro do build do frontend.

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | Tarefas com testes unit/`@WebMvcTest`/`@DataJpaTest` de uma ou poucas classes | `cd backend && mvn -q test -Dtest=<Classe>Test` (frontend: `cd frontend && npm test`) |
| Full | Tarefas que alteram segurança, medições, dashboard ou o fluxo integrado | `cd backend && mvn -q test` |
| Build | Fim de fase, tarefas de entidade/config/schema e tarefas de frontend | `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test` |

---

## Execution Plan

Phases are ordered and run sequentially - each phase completes before the next begins, and tasks within a phase execute in order.

### Phase 1: Configuração e utilitários de chave

```
T1 → T2
T1 → T4
```

T3 e T5 não dependem de outras tarefas.

### Phase 2: Modelo de dados

```
T6 → T7
T6 → T8 → T9
T6 → T10
T6 → T11
```

T12 não depende de outras tarefas.

### Phase 3: Medições, autoria, auditoria e notificações (base)

```
T13   T14   T15   T16   T17   T18   T19
```

Cada tarefa depende apenas de tarefas de fases anteriores.

### Phase 4: Agregação horária

```
T20 → T21 → T22 → T23
T21 → T24
```

### Phase 5: API de gerenciamento de chaves (ADMIN)

```
T25 → T26 → T27 → T28
```

### Phase 6: Autenticação do Arduino

```
T29 → T30 → T31
T29 → T33
T32 → T33
T33 → T34
```

### Phase 7: Ingestão de leituras

```
T35 → T36 → T37 → T38 → T39
```

### Phase 8: Dashboard ao vivo e alerta de sem sinal

```
T40 → T41
```

T42 não depende de outras tarefas da fase.

### Phase 9: Frontend

```
T45 → T47
T46 → T47
T47 → T48 → T49
T44 → T50
T47 → T50
T44 → T51
```

T43 não depende de outras tarefas da fase.

### Phase 10: Documentação e verificação final

```
T53 → T54
```

T52 não depende de outras tarefas da fase.

---

## Task Breakdown

### Phase 1: Configuração e utilitários de chave

### T1: Criar `DeviceProperties` e configuração de ambiente

**What**: Criar o record `@ConfigurationProperties(prefix = "app.device")` com `encryptionSecret`, `offlineMinutes` (padrão 5) e `rateLimitPerMinute` (padrão 10); ligar `DEVICE_KEY_ENCRYPTION_SECRET`, `DEVICE_OFFLINE_MINUTES` e `DEVICE_RATE_LIMIT_PER_MINUTE` em `application.yml`, `application-test.yml`, `.env.example` e `docker-compose.yml`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/config/DeviceProperties.java`
**Depends on**: None
**Reuses**: `config/OperatorAccessProperties.java` (record com default no construtor compacto)
**Requirement**: DEV-02, DEV-13

**Tools**:

- MCP: NONE
- Skill: `java-springboot`

**Done when**:

- [x] `DeviceProperties` com `@Validated`, `@Min(1)` em `offlineMinutes` e `rateLimitPerMinute`, defaults 5 e 10 quando 0
- [x] `application.yml` lê as três variáveis; `application-test.yml` define um segredo de teste com 32+ caracteres
- [x] `.env.example` e `docker-compose.yml` expõem `DEVICE_KEY_ENCRYPTION_SECRET` (obrigatória no compose), `DEVICE_OFFLINE_MINUTES`, `DEVICE_RATE_LIMIT_PER_MINUTE`
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente continua passando, nenhum teste removido

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add device properties and environment configuration`

---

### T2: Validar `DEVICE_KEY_ENCRYPTION_SECRET` na subida

**What**: Estender `SensitiveConfigurationValidator` para falhar quando o segredo da chave de dispositivo é placeholder conhecido ou tem menos de 32 caracteres.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/config/SensitiveConfigurationValidator.java`
**Depends on**: T1
**Reuses**: `rejectPlaceholder` e `FORBIDDEN_PLACEHOLDERS` existentes
**Requirement**: DEV-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Placeholder `replace_with_a_random_device_key_secret_with_32_chars` entra em `FORBIDDEN_PLACEHOLDERS` e é rejeitado com `IllegalStateException` nomeando `DEVICE_KEY_ENCRYPTION_SECRET`
- [x] Segredo com menos de 32 caracteres é rejeitado; segredo válido não lança
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=SensitiveConfigurationValidatorTest`
- [x] Test count: 3 testes novos passam (placeholder, curto, válido)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): validate device key encryption secret at startup`

---

### T3: Implementar `DeviceKeyGenerator`

**What**: Gerar a chave de 24 caracteres com `SecureRandom`, com ao menos uma maiúscula, uma minúscula, um número e um símbolo de `!@#$%^&*-_=+?`, embaralhada.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyGenerator.java`
**Depends on**: None
**Reuses**: nenhum (classe nova)
**Requirement**: DEV-01

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `generate()` devolve exatamente 24 caracteres, todos do alfabeto permitido
- [x] Em 1000 execuções, toda chave tem as quatro classes
- [x] Em 1000 execuções não há duas chaves iguais
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyGeneratorTest`
- [x] Test count: 4 testes passam (tamanho, alfabeto, classes, unicidade)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add secure device key generator`

---

### T4: Implementar `DeviceKeyCipher` (AES-GCM)

**What**: Cifrar e decifrar o valor da chave com AES-256-GCM; chave AES = SHA-256 de `DeviceProperties.encryptionSecret`; IV de 12 bytes aleatório prefixado; saída em Base64.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyCipher.java`
**Depends on**: T1
**Reuses**: `DeviceProperties`
**Requirement**: DEV-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `decrypt(encrypt(x))` devolve `x`
- [x] Duas cifragens do mesmo texto dão saídas diferentes (IV aleatório)
- [x] Saída não contém o texto original
- [x] Texto cifrado adulterado ou cifrado com outro segredo lança exceção na decifragem
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyCipherTest`
- [x] Test count: 4 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add AES-GCM cipher for device keys`

---

### T5: Implementar `DeviceKeyHasher`

**What**: Calcular o SHA-256 em hexadecimal minúsculo do valor da chave, usado para o login.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyHasher.java`
**Depends on**: None
**Reuses**: `java.security.MessageDigest`
**Requirement**: DEV-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `hash("abc")` devolve o vetor conhecido do SHA-256 (`ba7816bf...15ad`)
- [x] Mesma entrada dá mesma saída; entradas diferentes dão saídas diferentes
- [x] Saída tem 64 caracteres hexadecimais minúsculos
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyHasherTest`
- [x] Test count: 3 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add SHA-256 hasher for device keys`

---

### Phase 2: Modelo de dados

### T6: Criar a entidade `DeviceKey`

**What**: Entidade `device_keys` com ETE, nome, `nameKey` (minúsculas), `encryptedValue`, `valueHash` (único), `active`, `createdAt`, `createdBy`, `lastSeenAt`, últimos valores ao vivo (altura, litros, pH entrada, pH saída, cada um com horário) e `offlineNotifiedForSeenAt`; restrição única `(station_id, name_key)`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKey.java`
**Depends on**: None
**Reuses**: padrão de `measurement/FlowMeasurement.java` (UUID, `@PrePersist`)
**Requirement**: DEV-01, DEV-02, DEV-12, DEV-13

**Tools**:

- MCP: NONE
- Skill: `java-springboot`

**Done when**:

- [x] Entidade compila com getters e métodos de domínio `deactivate()`, `activate()`, `recordSeen(Instant)`, `recordLiveFlow(...)`, `recordLivePh(...)`
- [x] Hibernate cria a tabela no perfil `test` sem erro (suíte existente passa)
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add DeviceKey entity`

---

### T7: Criar `DeviceKeyRepository`

**What**: Repositório com `findByValueHash`, `existsByStationAndNameKey`, `findAllByOrderByNameKeyAsc`, `findAllByActiveTrueAndLastSeenAtIsNotNull` e consulta de chaves por lista de ETEs.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyRepository.java`
**Depends on**: T6
**Reuses**: padrão de `station/StationRepository.java`
**Requirement**: DEV-01, DEV-06, DEV-12, DEV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `findByValueHash` encontra pela chave e devolve vazio para hash desconhecido
- [x] `existsByStationAndNameKey` é verdadeiro só na mesma ETE com o mesmo `nameKey`
- [x] Segunda chave com mesmo `(station, nameKey)` viola a restrição única
- [x] Consulta por ETEs devolve só chaves das ETEs pedidas
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyRepositoryTest`
- [x] Test count: 4 testes passam

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): add DeviceKeyRepository`

---

### T8: Criar a entidade `DeviceReading` e o enum `DeviceReadingType`

**What**: Entidade `device_readings` (chave, tipo `FLOW|PH_RAW|PH_TREATED`, valor, litros, `measuredAt`, `createdAt`) com índice em `(device_key_id, type, measured_at)`, e o enum `DeviceReadingType`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReading.java`
**Depends on**: T6
**Reuses**: padrão de entidade de `FlowMeasurement`
**Requirement**: DEV-08, DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Entidade e enum compilam; tabela e índice criados no perfil `test`
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add DeviceReading entity`

---

### T9: Criar `DeviceReadingRepository`

**What**: Repositório com busca de leituras pendentes anteriores a um instante, busca por chave/tipo/intervalo e exclusão por chave e por ids.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReadingRepository.java`
**Depends on**: T8
**Reuses**: padrão de `measurement/FlowMeasurementRepository.java`
**Requirement**: DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `findPendingBefore(hourStart)` devolve só leituras com `measuredAt` anterior ao instante
- [x] Busca por intervalo respeita o limite inferior inclusivo e o superior exclusivo (`[14:00, 15:00)`)
- [x] Exclusão por chave remove só as leituras daquela chave
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceReadingRepositoryTest`
- [x] Test count: 4 testes passam

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): add DeviceReadingRepository`

---

### T10: Adaptar `FlowMeasurement` para registros de equipamento

**What**: Tornar `createdBy` opcional e adicionar `deviceKey` (nulável), `deviceName`, `deviceWindowEnd` e `sampleCount`, com índice único `(device_key_id, device_window_end)` e construtor/fábrica para registro de equipamento.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/FlowMeasurement.java`
**Depends on**: T6
**Reuses**: construtor existente de `FlowMeasurement`
**Requirement**: DEV-09, DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `createdBy` aceita nulo; registros manuais continuam exigindo autor no serviço
- [x] Persistir registro com autor nulo e `deviceWindowEnd` preenchido funciona no perfil `test`
- [x] Dois registros com mesma `(deviceKey, deviceWindowEnd)` violam a restrição
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `feat(device): allow device-authored flow measurements`

---

### T11: Adaptar `PhMeasurement` para registros de equipamento

**What**: Mesma adaptação de T10 em `PhMeasurement`, com índice único `(device_key_id, type, device_window_end)`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/PhMeasurement.java`
**Depends on**: T6
**Reuses**: mudança de T10
**Requirement**: DEV-09, DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `createdBy` aceita nulo; campos de equipamento persistem
- [x] Mesma `(deviceKey, deviceWindowEnd)` com tipos `RAW` e `TREATED` diferentes é permitida; mesmo tipo viola a restrição
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `feat(device): allow device-authored pH measurements`

---

### T12: Ajustar `schema.sql` para Postgres

**What**: Remover `NOT NULL` de `created_by_id` em `flow_measurements` e `ph_measurements` e recriar `audit_events_record_type_check` (com `DEVICE_KEY`) e `notifications_type_check` (com `DEVICE_OFFLINE_ALERT`, `DEVICE_BACK_ONLINE`), de forma idempotente.
**Where**: `backend/src/main/resources/schema.sql`
**Depends on**: None
**Reuses**: padrão `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT` de `app_users_role_check`
**Requirement**: DEV-05, DEV-11, DEV-13

**Tools**:

- MCP: NONE
- Skill: `supabase-postgres-best-practices`

**Done when**:

- [x] `ALTER COLUMN created_by_id DROP NOT NULL` para as duas tabelas, seguro para rodar a cada subida
- [x] Checks recriados listam todos os valores atuais do enum mais os novos (valores conferidos nos enums `AuditRecordType` e `NotificationType`)
- [x] Arquivo termina com `;` em todas as instruções
- [x] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [x] Test count: suíte existente intacta (a verificação em Postgres real é a T54)

**Tests**: none
**Gate**: build

**Commit**: `feat(device): update schema for device records and new enum values`

---

### Phase 3: Medições, autoria, auditoria e notificações (base)

### T13: Ajustar `FlowMeasurementRepository` para registros de equipamento

**What**: Mudar o filtro de autor das consultas de lista para `(:createdById is null or createdBy.id = :createdById or createdBy is null)` e adicionar `findByDeviceKeyAndDeviceWindowEnd`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/FlowMeasurementRepository.java`
**Depends on**: T10
**Reuses**: consultas `findForList` existentes
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Operador (filtro de autor preenchido) lista registros próprios e registros sem autor da ETE, e não lista registros de outro humano
- [x] Sem filtro de autor, todos aparecem; filtro de autor explícito do admin continua excluindo os sem autor
- [x] `findByDeviceKeyAndDeviceWindowEnd` encontra o registro, inclusive soft-deleted
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=FlowAuthorshipRepositoryTest,FlowMeasurementRepositoryTest`
- [x] Test count: 4 testes novos passam e os existentes continuam passando

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): list device flow records for station operators`

---

### T14: Ajustar `PhMeasurementRepository` para registros de equipamento

**What**: Mesma mudança de T13 nas consultas de pH, com `findByDeviceKeyAndTypeAndDeviceWindowEnd`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/PhMeasurementRepository.java`
**Depends on**: T11
**Reuses**: mudança de T13
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Operador lista registros próprios e registros sem autor; não lista os de outro humano
- [x] `findByDeviceKeyAndTypeAndDeviceWindowEnd` distingue `RAW` de `TREATED`
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=PhAuthorshipRepositoryTest,PhMeasurementRepositoryTest`
- [x] Test count: 4 testes novos passam e os existentes continuam passando

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): list device pH records for station operators`

---

### T15: Tornar `FlowMeasurementResponse` seguro para autor nulo

**What**: Quando não há autor, `createdById` fica nulo e `createdByName = "Equipamento: " + deviceName`; incluir `deviceName` e `sampleCount` na resposta.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/FlowMeasurementResponse.java`
**Depends on**: T10
**Reuses**: `FlowMeasurementResponse.from`
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Registro manual mantém `createdById` e `createdByName` como antes
- [x] Registro de equipamento devolve `createdById` nulo e `createdByName` igual a "Equipamento: Sensor Entrada"
- [x] `sampleCount` e `deviceName` aparecem para equipamento e ficam nulos para manual
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=FlowMeasurementResponseTest`
- [x] Test count: 3 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): expose device authorship in flow responses`

---

### T16: Tornar `PhMeasurementResponse` seguro para autor nulo

**What**: Mesmo tratamento de T15 na resposta de pH.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/measurement/PhMeasurementResponse.java`
**Depends on**: T11
**Reuses**: mudança de T15
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Registro manual inalterado; registro de equipamento devolve `createdById` nulo e rótulo "Equipamento: <nome>"
- [x] `deviceName` e `sampleCount` expostos
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=PhMeasurementResponseTest`
- [x] Test count: 3 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): expose device authorship in pH responses`

---

### T17: Permitir registros sem autor em `OperatorAuthorshipPolicy`

**What**: `assertOwned` passa a aceitar `createdBy == null` (registro de equipamento); a checagem de ETE continua em `StationScopeService`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/security/OperatorAuthorshipPolicy.java`
**Depends on**: None
**Reuses**: `assertOwned` existente
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] OPERATOR com `createdBy == null` não lança exceção
- [x] OPERATOR com `createdBy` de outro usuário continua recebendo `ResourceNotFoundException`
- [x] ADMIN/SUPERVISOR continuam sem restrição
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=OperatorAuthorshipPolicyTest`
- [x] Test count: testes existentes passam mais 1 novo (autor nulo)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): let operators act on device-authored records`

---

### T18: Adicionar `DEVICE_KEY` à auditoria

**What**: Incluir `DEVICE_KEY` em `AuditRecordType`, tratar o caso no `switch` de `AuditService.resolveRecordStation` (404 quando a chave não existe mais) e adicionar `existsByRecordTypeAndRecordIdAndAction` em `AuditEventRepository`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/audit/AuditService.java`
**Depends on**: T7
**Reuses**: `AuditService.recordUpdate/recordDelete`
**Requirement**: DEV-05, DEV-10

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Projeto compila com o novo valor de enum (switch exaustivo atendido)
- [x] `history(DEVICE_KEY, id, principal)` devolve eventos da chave existente e lança `ResourceNotFoundException` quando a chave não existe
- [x] `existsByRecordTypeAndRecordIdAndAction` é verdadeiro só para o tipo, id e ação certos
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=AuditServiceTest`
- [x] Test count: testes existentes passam mais 3 novos

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add DEVICE_KEY audit record type`

---

### T19: Adicionar tipos de notificação e destinatários de dispositivo

**What**: Incluir `DEVICE_OFFLINE_ALERT` e `DEVICE_BACK_ONLINE` em `NotificationType` e `deviceAlertRecipients(Station)` em `NotificationRecipientResolver` (supervisores e admins ativos mais operadores ativos da ETE, sem repetição).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/notification/NotificationRecipientResolver.java`
**Depends on**: None
**Reuses**: `activeSupervisorsAndAdmins`, `findAllByRoleAndStationAndActiveTrue`
**Requirement**: DEV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Enum tem os dois valores novos
- [x] Destinatários incluem ADMIN, SUPERVISOR e OPERATOR da mesma ETE
- [x] Operador de outra ETE, viewer e usuário inativo ficam de fora
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=NotificationRecipientResolverTest`
- [x] Test count: testes existentes passam mais 3 novos

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add device alert notification types and recipients`

---

### Phase 4: Agregação horária

### T20: `DeviceHourlyAggregator` - janela da hora e criação da média

**What**: Criar o componente com `windowEnd(Instant)` (fim da janela em `America/Sao_Paulo`) e a criação do registro de média de um grupo (chave, tipo, hora): vazão (média de `heightCm` e de litros, 2 casas, HALF_UP), pH (média, 2 casas, HALF_UP), `measuredAt` = `min(windowEnd, now)`, `createdBy` nulo, `deviceKey`, `deviceName`, `deviceWindowEnd`, `sampleCount`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceHourlyAggregator.java`
**Depends on**: T3, T9, T10, T11, T13, T14
**Reuses**: `FlowRateCalculator`, `SchedulingConfig.APP_ZONE`, `Clock`
**Requirement**: DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Leitura às 14:59:59 gera janela terminando 15:00; leitura às 15:00:00 gera janela terminando 16:00
- [x] 60 leituras de pH de 14:00 a 14:59 geram 1 registro às 15:00 com `sampleCount` 60 e média correta com HALF_UP
- [x] Vazão: `heightCm` é a média das alturas e `flowLiters` é a média dos litros de cada leitura
- [x] `RAW` para pH entrada e `TREATED` para pH saída
- [x] Registro tem `createdBy` nulo, `deviceName` igual ao nome da chave, `measuredAt` igual ao fim da janela
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceHourlyAggregatorTest`
- [x] Test count: 6 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add hourly window and average creation`

---

### T21: `DeviceHourlyAggregator` - rotina com catch-up idempotente

**What**: Implementar `aggregateClosedHours(Instant now)`: agrupa as leituras brutas de horas já fechadas por chave/tipo/hora, grava a média e apaga as brutas do grupo na mesma transação, com transação `REQUIRES_NEW` por grupo e log de falha por grupo.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceHourlyAggregator.java`
**Depends on**: T20
**Reuses**: método de criação de T20
**Requirement**: DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Horas fechadas pendentes viram registros e suas leituras brutas são apagadas; a hora corrente é intocada
- [x] Várias horas pendentes (servidor parado) são todas agregadas em uma execução
- [x] Executar duas vezes seguidas não duplica registros nem apaga leituras da hora aberta
- [x] Grupo sem leituras não gera registro
- [x] Falha em um grupo mantém suas leituras brutas e os demais grupos são agregados
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceHourlyAggregatorTest`
- [x] Test count: 5 testes novos passam (total ≥ 11)

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): aggregate closed hours with idempotent catch-up`

---

### T22: `DeviceHourlyAggregator` - reabertura de hora por leitura atrasada

**What**: Implementar `mergeLate(...)` com resultados `MERGED | RAW_STORED | DISCARDED_EDITED | DISCARDED_DELETED | DISCARDED_TOO_OLD`: se o registro da hora existe, descarta quando excluído ou quando há evento de auditoria UPDATED; senão recalcula pela média ponderada `(m×n + x)/(n+1)` e incrementa `sampleCount`; hora fechada sem registro e dentro de 24 h guarda leitura bruta; hora fechada há mais de 24 h descarta.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceHourlyAggregator.java`
**Depends on**: T21, T18
**Reuses**: `AuditEventRepository.existsByRecordTypeAndRecordIdAndAction`, repositórios de T13/T14
**Requirement**: DEV-10

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Registro com média 7,00 e `sampleCount` 60 recebe leitura 8,00 e passa a 7,02 com `sampleCount` 61 (HALF_UP), mantendo `measuredAt`
- [x] Registro editado (evento UPDATED) devolve `DISCARDED_EDITED` e permanece inalterado
- [x] Registro excluído devolve `DISCARDED_DELETED` e permanece excluído e inalterado
- [x] Leitura de hora fechada há mais de 24 h devolve `DISCARDED_TOO_OLD` e não cria leitura bruta nem registro
- [x] Hora fechada sem registro e com até 24 h devolve `RAW_STORED` e grava a leitura bruta
- [x] Vazão recalcula `heightCm` e `flowLiters` ponderados
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceHourlyAggregatorTest`
- [x] Test count: 7 testes novos passam (total ≥ 18)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): reopen closed hours for late readings`

---

### T23: `DeviceHourlyAggregator` - agregação na exclusão da chave

**What**: Implementar `aggregateKeyIncludingPartial(DeviceKey, Instant now)`: agrega todas as leituras brutas pendentes da chave, inclusive a hora parcial (com `measuredAt` = `now`), e apaga as brutas.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceHourlyAggregator.java`
**Depends on**: T22
**Reuses**: criação e fusão de T20 e T22
**Requirement**: DEV-09, DEV-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Leituras da hora corrente viram um registro com `measuredAt` igual a `now` e `deviceWindowEnd` igual ao fim da hora
- [x] Horas fechadas pendentes da mesma chave também são agregadas
- [x] Nenhuma leitura bruta da chave sobra; leituras de outras chaves não são tocadas
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceHourlyAggregatorTest`
- [x] Test count: 3 testes novos passam (total ≥ 21)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): aggregate pending readings when a key is deleted`

---

### T24: Criar `DeviceHourlyScheduler`

**What**: Agendador `@Scheduled(cron = "30 0 * * * *", zone = "America/Sao_Paulo")` que chama `aggregateClosedHours(now)` com `Clock` injetado e captura exceções com log.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceHourlyScheduler.java`
**Depends on**: T21
**Reuses**: `notification/schedule/NotificationScheduler.java`
**Requirement**: DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `run()` chama `aggregateClosedHours` com o instante do `Clock`
- [x] Exceção do agregador não sobe do agendador
- [x] Anotação de cron e zona conferidas por reflexão no teste
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceHourlySchedulerTest`
- [x] Test count: 3 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): schedule hourly aggregation`

---

### Phase 5: API de gerenciamento de chaves (ADMIN)

### T25: `DeviceKeyService` - criar, listar e obter o valor

**What**: Implementar criação (gera, cifra, calcula hash, nome único por ETE sem diferenciar maiúsculas, 409 se duplicado), listagem (sem valor, com `status` online/offline/never pela regra de `offlineMinutes`) e `secret(id)` (decifra sob demanda).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyService.java`
**Depends on**: T3, T4, T5, T7, T1
**Reuses**: `StationService.requireStation`, `CurrentUserService`, `BusinessException`
**Requirement**: DEV-01, DEV-02, DEV-13

**Tools**:

- MCP: NONE
- Skill: `java-springboot`

**Done when**:

- [x] Criar com nome e ETE devolve item ativo; o valor gravado é cifrado e o hash corresponde ao valor
- [x] Nome igual em maiúsculas/minúsculas diferentes na mesma ETE gera `BusinessException` (409); em outra ETE é permitido
- [x] Listagem devolve id, nome, ETE, estado, criação, último envio e status, sem campo de valor
- [x] Status: `ONLINE` até 5 min, `OFFLINE` acima, `NEVER` sem envio (usando `Clock` fixo)
- [x] `secret(id)` devolve o valor original; falha de decifragem lança erro genérico sem texto da chave; id inexistente dá 404
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyServiceTest`
- [x] Test count: 8 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): create, list and reveal device keys`

---

### T26: `DeviceKeyService` - desativar e reativar com auditoria

**What**: `setActive(id, active, admin)` que altera o estado e registra evento de auditoria `DEVICE_KEY` com descrição fixa (sem o valor da chave).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyService.java`
**Depends on**: T25, T18
**Reuses**: `AuditService.recordUpdate`
**Requirement**: DEV-04, DEV-05

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Desativar muda `active` para falso e grava evento `DEVICE_KEY` com usuário, ETE e descrição de 10+ caracteres
- [x] Reativar volta a verdadeiro e grava evento
- [x] Descrição do evento não contém o valor da chave nem o hash
- [x] Id inexistente dá `ResourceNotFoundException`
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyServiceTest`
- [x] Test count: 4 testes novos passam (total ≥ 12)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): deactivate and reactivate device keys with audit`

---

### T27: `DeviceKeyService` - excluir chave

**What**: `delete(id, admin)`: agrega as leituras pendentes (`aggregateKeyIncludingPartial`), anula `device_key_id` nos registros de média por UPDATE em lote (mantendo `deviceName`), remove a chave e seus valores ao vivo e registra auditoria `DEVICE_KEY` (ação DELETED).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyService.java`
**Depends on**: T26, T23
**Reuses**: `DeviceHourlyAggregator`, `AuditService.recordDelete`
**Requirement**: DEV-04, DEV-05, DEV-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] A ordem é agregar, anular referências, auditar e excluir, tudo em uma transação
- [x] Registros de média da chave permanecem com `deviceKey` nulo e `deviceName` preservado
- [x] Evento de auditoria DELETED registrado sem o valor da chave
- [x] Exclusão de id inexistente dá 404
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyServiceTest`
- [x] Test count: 4 testes novos passam (total ≥ 16)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): delete device keys preserving hourly records`

---

### T28: Criar `DeviceKeyController`

**What**: Controller `/api/device-keys` com `GET` (lista), `POST` (cria), `GET /{id}/secret`, `PATCH /{id}/active` e `DELETE /{id}`, todos com `@PreAuthorize("hasRole('ADMIN')")`, mais os DTOs `DeviceKeyResponse`, `DeviceKeyCreateRequest`, `DeviceKeySecretResponse` e `DeviceKeyActiveRequest`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceKeyController.java`
**Depends on**: T27
**Reuses**: padrão de `measurement/FlowMeasurementController.java` e `@AuthenticationPrincipal UserPrincipal`
**Requirement**: DEV-01, DEV-02, DEV-03, DEV-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] ADMIN: lista 200, cria 200/201, secret 200, ativa/desativa 200, exclui 204
- [x] OPERATOR, SUPERVISOR e VIEWER recebem 403 em todas as rotas
- [x] Nome vazio ou ETE ausente recebe 400; nome duplicado recebe 409 (via `GlobalExceptionHandler`)
- [x] Resposta de lista e de criação não contém o campo do valor da chave
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceKeyControllerTest`
- [x] Test count: 12 testes passam

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): add admin device key endpoints`

---

### Phase 6: Autenticação do Arduino

### T29: Emitir JWT do dispositivo em `JwtService`

**What**: Adicionar `generateDeviceAccessToken(DeviceKey)` (subject `device:<id>`, claims `role=DEVICE`, `deviceKeyId`, `stationId`, `tokenType=access`), `isDeviceToken(String)` e `deviceKeyId(String)`; criar `DevicePrincipal` (`UserDetails` com autoridade `ROLE_DEVICE`, `deviceKeyId`, `stationId`, `name`).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/security/JwtService.java`
**Depends on**: T7
**Reuses**: `generateAccessToken` e `parseClaims`
**Requirement**: DEV-06

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Token de dispositivo tem `role=DEVICE`, `deviceKeyId` e `stationId` corretos e expiração igual a `accessTokenMinutes`
- [x] `isDeviceToken` é verdadeiro só para token de dispositivo; `isAccessToken` continua verdadeiro para ele
- [x] Token de usuário não é reconhecido como de dispositivo
- [x] `DevicePrincipal` expõe `ROLE_DEVICE`
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=JwtServiceTest`
- [x] Test count: 5 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): issue device JWT tokens`

---

### T30: Autenticar o DEVICE em `JwtAuthenticationFilter`

**What**: No filtro, quando o token é de dispositivo, carregar a chave pelo id, exigir `active` e montar `DevicePrincipal`; chave ausente ou desativada deixa a requisição sem autenticação (401).
**Where**: `backend/src/main/java/br/com/carbonifera/ete/security/JwtAuthenticationFilter.java`
**Depends on**: T29
**Reuses**: `authenticate` existente
**Requirement**: DEV-07, DEV-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Token de dispositivo com chave ativa autentica com `ROLE_DEVICE`
- [x] Chave desativada, excluída ou inexistente não autentica e a requisição segue sem contexto de segurança
- [x] Token de usuário continua autenticando como antes
- [x] Token adulterado continua sendo ignorado
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=JwtAuthenticationFilterTest`
- [x] Test count: 5 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): authenticate device tokens in the JWT filter`

---

### T31: Isolar o papel DEVICE em `SecurityConfig`

**What**: Liberar `POST /api/auth/device-login`, restringir `POST /api/device/readings` a `hasRole('DEVICE')` e trocar `anyRequest().authenticated()` por `hasAnyRole('OPERATOR','SUPERVISOR','ADMIN','VIEWER')`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/security/SecurityConfig.java`
**Depends on**: T30
**Reuses**: regras existentes de `authorizeHttpRequests`
**Requirement**: DEV-07

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] DEVICE recebe 403 em rotas humanas amostrais (`/api/flow-measurements`, `/api/dashboard`, `/api/auth/change-password`, `/api/notifications`, `/api/device-keys`)
- [x] Papéis humanos recebem 403 em `/api/device/readings`
- [x] `POST /api/auth/device-login` é acessível sem token; sem token em rota protegida segue 401
- [x] Suíte de segurança existente (`SecurityConfigTest`, `OperationalWriteAuthorizationTest`, `ViewerAuthorizationTest`, `ReportAuthorizationTest`) continua passando
- [x] Gate check passes: `cd backend && mvn -q test`
- [x] Test count: 6 testes novos passam e nenhum existente falha

**Tests**: integration
**Gate**: full

**Commit**: `feat(device): restrict the DEVICE role to the readings endpoint`

---

### T32: Implementar `DeviceLoginThrottle`

**What**: Limitador em memória por IP: 10 falhas em 5 minutos bloqueiam o IP por 15 minutos; sucesso zera as falhas; `Clock` injetado.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceLoginThrottle.java`
**Depends on**: None
**Reuses**: `Clock` do `SchedulingConfig`
**Requirement**: DEV-06

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] 9 falhas em 5 minutos não bloqueiam; a 10ª bloqueia por 15 minutos
- [x] Falhas fora da janela de 5 minutos não contam
- [x] Após 15 minutos o IP é liberado
- [x] Sucesso zera o contador; IPs diferentes são independentes
- [x] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceLoginThrottleTest`
- [x] Test count: 5 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add device login throttle`

---

### T33: Implementar `DeviceAuthService`

**What**: `login(String key, String ip)`: rejeita bloqueio (429), valida formato (24 caracteres), busca por hash, exige chave ativa, emite o token e registra falha ou sucesso no limitador; falhas retornam sempre o mesmo erro genérico.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceAuthService.java`
**Depends on**: T29, T32
**Reuses**: `DeviceKeyHasher`, `DeviceKeyRepository`, `JwtService`
**Requirement**: DEV-06

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Chave ativa válida devolve token de dispositivo e `expiresInSeconds`, sem refresh token
- [ ] Chave desconhecida, desativada, nula ou de tamanho diferente de 24 lança o mesmo erro de autenticação e conta como falha
- [ ] IP bloqueado lança erro de excesso de tentativas sem consultar o banco
- [ ] Sucesso zera as falhas do IP
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceAuthServiceTest`
- [ ] Test count: 6 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): authenticate devices by key`

---

### T34: Criar `DeviceAuthController`

**What**: `POST /api/auth/device-login` com corpo `{key}`; 200 com `{accessToken, tokenType, expiresInSeconds}`, 401 genérico para falha, 429 para IP bloqueado; IP via `request.getRemoteAddr()`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceAuthController.java`
**Depends on**: T33
**Reuses**: `security/AuthController.java`, `GlobalExceptionHandler`
**Requirement**: DEV-06

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Chave válida responde 200 com os três campos e sem refresh token
- [ ] Chave inválida, desativada ou corpo sem `key` responde 401 com corpo idêntico
- [ ] IP bloqueado responde 429 sem informar se a chave existe
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceAuthControllerTest`
- [ ] Test count: 5 testes passam

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): add device login endpoint`

---

### Phase 7: Ingestão de leituras

### T35: Implementar `DeviceRateLimiter`

**What**: Limitador por chave de janela fixa de 1 minuto, com limite vindo de `DeviceProperties.rateLimitPerMinute`, em memória, `Clock` injetado.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceRateLimiter.java`
**Depends on**: T1
**Reuses**: `DeviceProperties`, `Clock`
**Requirement**: DEV-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] As primeiras 10 chamadas no minuto passam e a 11ª é negada
- [ ] No minuto seguinte o contador reinicia
- [ ] Chaves diferentes têm contadores independentes
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceRateLimiterTest`
- [ ] Test count: 4 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add per-key rate limiter`

---

### T36: `DeviceReadingService` - validar e gravar leituras brutas

**What**: `ingest(DevicePrincipal, DeviceReadingRequest)`: aplica o limite (`DeviceRateLimiter`), exige ao menos um valor, valida faixas (`heightCm` 0–45, pH 0–14, 2 casas), ignora valores inválidos listando `rejected`, resolve `measuredAt` (hora do servidor se ausente; 400 se no futuro), calcula `flowLiters` com `FlowRateCalculator` e grava uma `DeviceReading` por valor aceito.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReadingService.java`
**Depends on**: T35, T9
**Reuses**: `FlowRateCalculator`, faixas de `FlowMeasurementRequest` e `PhMeasurementRequest`
**Requirement**: DEV-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Corpo sem nenhum valor lança 400; corpo só com valores inválidos lança 400 e não grava
- [ ] Apenas os campos enviados e válidos geram leitura; os outros não geram nada
- [ ] `heightCm` 46, pH 14,5 e valores com 3 casas são rejeitados e listados em `rejected` enquanto os válidos são aceitos
- [ ] Sem `measuredAt` usa a hora do `Clock`; `measuredAt` no futuro lança 400
- [ ] 11ª requisição no minuto lança erro de excesso (429) e não grava
- [ ] `flowLiters` da leitura de vazão é o de `FlowRateCalculator`
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceReadingServiceTest`
- [ ] Test count: 9 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): validate and store device readings`

---

### T37: `DeviceReadingService` - último envio e valores ao vivo

**What**: A cada leitura aceita, atualizar na `DeviceKey` o `lastSeenAt` (hora do servidor) e os últimos valores ao vivo (altura e litros, pH entrada, pH saída) com o `measuredAt` da leitura.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReadingService.java`
**Depends on**: T36
**Reuses**: `DeviceKey.recordSeen`, `recordLiveFlow`, `recordLivePh`
**Requirement**: DEV-08, DEV-12

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Envio com os três valores atualiza os três pares valor/horário e `lastSeenAt`
- [ ] Envio só de pH entrada não altera os valores ao vivo dos outros tipos
- [ ] Leitura rejeitada não altera valores ao vivo; requisição sem valor válido não altera `lastSeenAt`
- [ ] Leitura com `measuredAt` mais antigo que o valor ao vivo atual não o substitui
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceReadingServiceTest`
- [ ] Test count: 4 testes novos passam (total ≥ 13)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): track last seen and live values`

---

### T38: `DeviceReadingService` - leituras de horas fechadas

**What**: Classificar cada leitura pelo `measuredAt`: hora ainda aberta grava bruta; hora fechada há mais de 24 h é descartada; demais casos delegam a `DeviceHourlyAggregator.mergeLate`; a resposta lista `discarded` com o motivo.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReadingService.java`
**Depends on**: T37, T22
**Reuses**: `DeviceHourlyAggregator.mergeLate`
**Requirement**: DEV-10

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Leitura de hora aberta vira `DeviceReading` e não chama `mergeLate`
- [ ] Leitura de hora fechada há até 24 h chama `mergeLate` com o valor e o tipo certos
- [ ] Resultado descartado (editada, excluída, antiga) vira 200 com `discarded` e log, sem gravar bruta
- [ ] Leitura exatamente no limite de uma hora (15:00:00) pertence à janela que começa às 15:00
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceReadingServiceTest`
- [ ] Test count: 5 testes novos passam (total ≥ 18)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): route late readings through hourly reopening`

---

### T39: Criar `DeviceReadingController`

**What**: `POST /api/device/readings` com `@PreAuthorize("hasRole('DEVICE')")`, corpo `{heightCm?, rawPh?, treatedPh?, measuredAt?}` e resposta `{accepted, rejected, discarded}`; mapear erros para 400 e 429.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceReadingController.java`
**Depends on**: T38
**Reuses**: `GlobalExceptionHandler`, `@AuthenticationPrincipal`
**Requirement**: DEV-07, DEV-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] DEVICE com corpo válido recebe 200 com `accepted`, `rejected` e `discarded`
- [ ] Corpo vazio ou só com inválidos recebe 400
- [ ] Excesso de requisições recebe 429
- [ ] Papéis humanos recebem 403 na rota
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceReadingControllerTest`
- [ ] Test count: 6 testes passam

**Tests**: integration
**Gate**: quick

**Commit**: `feat(device): add device readings endpoint`

---

### Phase 8: Dashboard ao vivo e alerta de sem sinal

### T40: Criar `DeviceLiveReadingService`

**What**: Serviço que devolve, por lista de ETEs, o valor ao vivo mais recente de vazão, pH entrada e pH saída como `DashboardMeasurementResponse` (unidades "L" e "pH").
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceLiveReadingService.java`
**Depends on**: T37
**Reuses**: `DashboardMeasurementResponse`, `DeviceKeyRepository`
**Requirement**: DEV-12

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Com várias chaves na ETE, devolve o valor de `measuredAt` mais recente por tipo
- [ ] Chave sem leitura do tipo é ignorada; sem nenhuma devolve vazio
- [ ] Considera só as ETEs pedidas
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceLiveReadingServiceTest`
- [ ] Test count: 5 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): expose latest live readings per station`

---

### T41: Usar valores ao vivo em `DashboardService`

**What**: `latestFlow`, `latestRawPh` e `latestTreatedPh` passam a devolver o de `measuredAt` maior entre o último registro das tabelas e o valor ao vivo; séries, médias do dia e totais permanecem somente com os registros das tabelas.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/dashboard/DashboardService.java`
**Depends on**: T40
**Reuses**: `latest*` existentes, `DeviceLiveReadingService`
**Requirement**: DEV-12, DEV-14

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Valor ao vivo mais novo que o último registro aparece no card, com o horário dele
- [ ] Registro manual mais novo que o valor ao vivo aparece no lugar dele
- [ ] Sem valor ao vivo, o comportamento é idêntico ao atual
- [ ] Total e média de vazão do dia, séries e atividade não mudam quando só há valores ao vivo
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DashboardServiceTest`
- [ ] Test count: testes existentes passam mais 5 novos

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): show live device values on the dashboard`

---

### T42: Criar `DeviceOfflineMonitor`

**What**: Monitor `@Scheduled(fixedRate = 60000)` que, para chaves ativas com `lastSeenAt`, dispara `DEVICE_OFFLINE_ALERT` uma vez por queda (dedupe `DEVICE_OFFLINE|<keyId>|<lastSeenAt>`, controle em `offlineNotifiedForSeenAt`) e `DEVICE_BACK_ONLINE` quando volta, aos destinatários de `deviceAlertRecipients`.
**Where**: `backend/src/main/java/br/com/carbonifera/ete/device/DeviceOfflineMonitor.java`
**Depends on**: T19, T7
**Reuses**: `lime/LimeMinStockMonitor.java`, `NotificationDispatcher`, `NotificationCommand`
**Requirement**: DEV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Chave ativa com último envio há mais de `offlineMinutes` dispara 1 alerta com nome da chave e ETE
- [ ] Segunda execução sem novo envio não dispara outro alerta
- [ ] Novo envio após o alerta dispara `DEVICE_BACK_ONLINE` uma vez e libera novo alerta em queda futura
- [ ] Chave desativada ou sem nenhum envio não gera alerta
- [ ] Destinatários vêm de `deviceAlertRecipients(station)`
- [ ] Gate check passes: `cd backend && mvn -q test -Dtest=DeviceOfflineMonitorTest`
- [ ] Test count: 7 testes passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): alert when a device stops sending`

---

### Phase 9: Frontend

### T43: Tratar autor nulo nos adaptadores do frontend

**What**: Em `types.ts` e `utils/apiAdapters.ts`, aceitar `createdById` e `createdByName` nulos e `deviceName`/`sampleCount` opcionais; o campo `operator` usa o nome ou o rótulo do equipamento.
**Where**: `frontend/src/utils/apiAdapters.ts`
**Depends on**: None
**Reuses**: adaptadores existentes (`operator: response.createdByName`)
**Requirement**: DEV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Resposta com autor humano mapeia `operator` como antes
- [ ] Resposta com `createdById` nulo e `createdByName` "Equipamento: X" mapeia `operator` para esse texto
- [ ] `createdByName` nulo cai em texto padrão sem lançar erro
- [ ] Gate check passes: `cd frontend && npm test`
- [ ] Test count: 3 testes novos passam e os existentes continuam passando

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): handle equipment authorship in frontend adapters`

---

### T44: Adicionar `canManageDeviceKeys` às permissões

**What**: Função `canManageDeviceKeys(role)` que retorna verdadeiro só para ADMIN.
**Where**: `frontend/src/auth/permissions.ts`
**Depends on**: None
**Reuses**: `canManageUsers`
**Requirement**: DEV-03

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] ADMIN verdadeiro; OPERATOR, SUPERVISOR, VIEWER e indefinido falsos
- [ ] Gate check passes: `cd frontend && npm test`
- [ ] Test count: 2 testes novos passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add device key permission`

---

### T45: Criar utilitários de chaves de dispositivo

**What**: `utils/deviceKeys.ts` com `isDeleteConfirmed(text)` (exatamente `EXCLUIR`), `SECRET_VISIBLE_MS = 15000` e `statusLabel(status)` (Online, Offline, Sem sinal ainda).
**Where**: `frontend/src/utils/deviceKeys.ts`
**Depends on**: None
**Reuses**: padrão de `utils/flowHeight.ts`
**Requirement**: DEV-03, DEV-04, DEV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `isDeleteConfirmed("EXCLUIR")` verdadeiro; "excluir", " EXCLUIR", "EXCLUIR " e vazio falsos
- [ ] `SECRET_VISIBLE_MS` vale 15000
- [ ] `statusLabel` cobre `ONLINE`, `OFFLINE` e `NEVER`
- [ ] Gate check passes: `cd frontend && npm test`
- [ ] Test count: 4 testes novos passam

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add device key frontend helpers`

---

### T46: Criar cliente de API `deviceKeyApi`

**What**: Funções `listDeviceKeys`, `createDeviceKey`, `getDeviceKeySecret`, `setDeviceKeyActive` e `deleteDeviceKey` sobre `/api/device-keys`, com tipos `DeviceKeyItem` e payloads.
**Where**: `frontend/src/services/deviceKeyApi.ts`
**Depends on**: None
**Reuses**: `services/userApi.ts`, `services/api.ts`
**Requirement**: DEV-03, DEV-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Cada função monta método, caminho e corpo exatos (`PATCH /{id}/active` com `{active}`, `DELETE /{id}`, `GET /{id}/secret`)
- [ ] Erros da API chegam à tela como mensagem legível
- [ ] Gate check passes: `cd frontend && npm test`
- [ ] Test count: 5 testes novos passam (payloads)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(device): add device key API client`

---

### T47: Criar `AutomationPage` - lista e nova chave

**What**: Página com cabeçalho, listagem (nome, ETE, último envio, selo online/offline/sem sinal ainda, estado ativa/desativada) e formulário de nova chave (nome + seleção de ETE); estilos em `styles.css`.
**Where**: `frontend/src/pages/AutomationPage.tsx`
**Depends on**: T45, T46
**Reuses**: `pages/UsersAdminPage.tsx`, `StationField`, `PageHeader`, `DataState`, `useAsyncResource`
**Requirement**: DEV-03, DEV-13

**Tools**:

- MCP: NONE
- Skill: `frontend-design`

**Done when**:

- [ ] Lista carrega e mostra estados de carregando, vazio e erro
- [ ] Criar chave com nome e ETE atualiza a lista; erro 409 mostra mensagem de nome duplicado
- [ ] Nenhuma linha exibe o valor da chave
- [ ] Layout funciona em tela estreita (mobile) sem rolagem horizontal da página
- [ ] Gate check passes: `cd frontend && npm run build`
- [ ] Test count: build e testes do frontend passam sem remoções

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add automation page with key list and creation`

---

### T48: `AutomationPage` - ver e copiar a chave

**What**: Por linha, botão "ver" (toggle Eye/EyeOff que busca o valor sob demanda e o oculta sozinho após `SECRET_VISIBLE_MS`) e botão "copiar" (busca o valor e copia para a área de transferência sem exibi-lo).
**Where**: `frontend/src/pages/AutomationPage.tsx`
**Depends on**: T47
**Reuses**: toggle de `pages/LoginPage.tsx`
**Requirement**: DEV-03

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] "Ver" alterna entre exibir e ocultar; ao exibir mostra o valor retornado por `/secret`
- [ ] O valor some sozinho após 15 segundos e ao navegar para fora da página
- [ ] "Copiar" copia o valor e dá retorno visual de sucesso sem mostrar a chave
- [ ] Falha ao buscar ou copiar mostra mensagem de erro e não deixa valor na tela
- [ ] Gate check passes: `cd frontend && npm run build`
- [ ] Test count: build e testes do frontend passam sem remoções

**Tests**: none
**Gate**: build

**Commit**: `feat(device): reveal and copy device keys on the automation page`

---

### T49: `AutomationPage` - desativar, reativar e excluir

**What**: Botões de desativar/reativar com confirmação e de excluir com confirmação e campo que exige digitar `EXCLUIR` (botão de confirmar desabilitado até `isDeleteConfirmed`).
**Where**: `frontend/src/pages/AutomationPage.tsx`
**Depends on**: T48
**Reuses**: `isDeleteConfirmed`, `setDeviceKeyActive`, `deleteDeviceKey`
**Requirement**: DEV-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Desativar e reativar pedem confirmação e atualizam o estado na lista ao confirmar
- [ ] Excluir abre confirmação com campo de texto; o botão de confirmar fica desabilitado até digitar exatamente `EXCLUIR`
- [ ] Cancelar não altera nada; excluir com sucesso remove a linha
- [ ] Gate check passes: `cd frontend && npm run build`
- [ ] Test count: build e testes do frontend passam sem remoções

**Tests**: none
**Gate**: build

**Commit**: `feat(device): manage key state and deletion on the automation page`

---

### T50: Registrar a rota `/app/automacao`

**What**: Adicionar a rota `automacao` em `App.tsx` protegida para ADMIN, no mesmo padrão da rota `usuarios`.
**Where**: `frontend/src/App.tsx`
**Depends on**: T44, T47
**Reuses**: rota `usuarios` e `ProtectedRoute`
**Requirement**: DEV-03

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `/app/automacao` renderiza `AutomationPage` para ADMIN
- [ ] Usuário não ADMIN é redirecionado como na rota de usuários
- [ ] Gate check passes: `cd frontend && npm run build`
- [ ] Test count: build e testes do frontend passam sem remoções

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add automation route for admins`

---

### T51: Adicionar o botão "Automação" na aba Mais

**What**: Em `MorePage.tsx`, novo `quick-link-card` "Automação" com ícone `Cpu` apontando para `/app/automacao`, visível só quando `canManageDeviceKeys`.
**Where**: `frontend/src/pages/MorePage.tsx`
**Depends on**: T44
**Reuses**: botão "Usuários" de `MorePage.tsx`
**Requirement**: DEV-03

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] ADMIN vê o botão ao lado de Relatórios, Vazão e demais
- [ ] Outros perfis não veem o botão
- [ ] Gate check passes: `cd frontend && npm run build`
- [ ] Test count: build e testes do frontend passam sem remoções

**Tests**: none
**Gate**: build

**Commit**: `feat(device): add automation shortcut to the More tab`

---

### Phase 10: Documentação e verificação final

### T52: Documentar a implantação e o protocolo do Arduino

**What**: Seção no `README.md` com a variável `DEVICE_KEY_ENCRYPTION_SECRET` (e a perda das chaves se for trocada), exigência de HTTPS via proxy reverso com repasse do IP real, fluxo `device-login` → `readings` com exemplos `curl`, regras de 401 (refazer login), 429 e frequência de 1 minuto.
**Where**: `README.md`
**Depends on**: None
**Reuses**: README atual
**Requirement**: DEV-02, DEV-06, DEV-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Exemplos de login e envio com todos os campos opcionais descritos
- [ ] Riscos de HTTPS e de IP atrás do proxy registrados
- [ ] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [ ] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `docs(device): document device keys deployment and Arduino protocol`

---

### T53: Teste de integração ponta a ponta do backend

**What**: `@SpringBootTest` com H2, `MockMvc` e `Clock` controlado: admin cria chave, Arduino faz login, envia leituras por 1 hora, a rotina agrega, lista de medições e dashboard refletem só a média, valor ao vivo aparece no card, DEVICE recebe 403 em rotas humanas, desativar gera 401 na próxima requisição e excluir preserva os registros.
**Where**: `backend/src/test/java/br/com/carbonifera/ete/device/DeviceEndToEndIntegrationTest.java`
**Depends on**: T39, T41, T42, T31, T28
**Reuses**: `application-test.yml`, `@SpringBootTest`, `MockMvc`
**Requirement**: DEV-04, DEV-07, DEV-09, DEV-11, DEV-12, DEV-14

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] 60 leituras de 14:00 a 14:59 resultam em 1 registro às 15:00 e 0 leituras brutas da hora
- [ ] Operador da ETE vê "Equipamento: <nome>" na lista de vazão; operador de outra ETE não vê
- [ ] Total de vazão do dia e séries do dashboard não mudam com valores ao vivo; `latestFlow` mostra o valor ao vivo mais novo
- [ ] DEVICE recebe 403 em `/api/flow-measurements`, `/api/dashboard` e `/api/device-keys`
- [ ] Após desativar, o mesmo JWT recebe 401 em `/api/device/readings`; após excluir, registros ficam com `deviceName` e sem chave
- [ ] Gate check passes: `cd backend && mvn -q test`
- [ ] Test count: 7 testes novos passam e nenhum existente falha

**Tests**: integration
**Gate**: full

**Commit**: `test(device): add end-to-end device flow integration test`

---

### T54: Verificar `schema.sql` em Postgres real

**What**: Subir o Postgres do `docker-compose`, iniciar o backend com os ajustes e confirmar que as colunas aceitam nulo e que os checks de `audit_events` e `notifications` aceitam os valores novos; registrar os nomes reais dos checks (`\d audit_events`, `\d notifications`) em nota na própria tarefa.
**Where**: `backend/src/main/resources/schema.sql`
**Depends on**: T53
**Reuses**: `docker-compose.yml`
**Requirement**: DEV-05, DEV-11, DEV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Backend sobe contra Postgres 16 sem erro de SQL
- [ ] `INSERT` em `audit_events` com `record_type = 'DEVICE_KEY'` e em `notifications` com `type = 'DEVICE_OFFLINE_ALERT'` e `'DEVICE_BACK_ONLINE'` é aceito
- [ ] Registro de média sem `created_by_id` é aceito em `flow_measurements` e `ph_measurements`
- [ ] Se os nomes dos checks diferirem do assumido, `schema.sql` é corrigido e a correção é descrita no commit
- [ ] Gate check passes: `cd backend && mvn -q verify && cd ../frontend && npm run build && npm test`
- [ ] Test count: suíte existente intacta

**Tests**: none
**Gate**: build

**Commit**: `fix(device): confirm schema constraints against PostgreSQL`

---

## Phase Execution Map

Phases run in sequence, and tasks within a phase run in order:

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8 → Phase 9 → Phase 10
```

| Phase | Tasks | Observação |
| ----- | ----- | ---------- |
| 1 | T1-T5 | T3 e T5 livres; T2 e T4 dependem de T1 |
| 2 | T6-T12 | T12 livre; demais dependem de T6 |
| 3 | T13-T19 | Sem dependência interna |
| 4 | T20-T24 | Cadeia T20, T21, T22, T23; T24 depende de T21 |
| 5 | T25-T28 | Cadeia linear |
| 6 | T29-T34 | T33 depende de T29 e T32 |
| 7 | T35-T39 | Cadeia linear |
| 8 | T40-T42 | T41 depende de T40; T42 livre |
| 9 | T43-T51 | T43 livre; T47 depende de T45 e T46 |
| 10 | T52-T54 | T52 livre; T54 depende de T53 |

Execution is strictly sequential - there is no intra-phase parallelism. A single agent (or batch worker) works one task at a time, in order. Com 54 tarefas, o Execute empacota fases inteiras em lotes de ~7 tarefas e oferece sub-agentes por lote antes de começar.
