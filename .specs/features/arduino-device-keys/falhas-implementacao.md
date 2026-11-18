# Falhas de implementação

## 2026-11-12 — T1, tentativa 1

- Modelo: gpt-6-luna (medium); commit `c1c4b87`.
- Evidência: `mvn -q verify` falhou ao resolver descritores de dependências do plugin `spring-boot:repackage`, inclusive após tentativa com `-U`. Antes disso, 331 testes Maven passaram. Build do frontend e 69 testes passaram separadamente.
- Resultado: gate de build não aprovado; nova tentativa com gpt-6.1-sol (medium) pendente.
- Desfecho da repetição com gpt-6.1-sol: causa identificada como truststore do JDK sem trust anchors; gate completo passou usando `MAVEN_OPTS` com truststore do sistema `/etc/ssl/certs/java/cacerts`. Nenhuma mudança de código foi necessária. Verificação independente pendente.
- Verificador independente: aprovado; gate completo repetido com o truststore do sistema.

## 2026-11-13 — T21/T22, integração da tentativa 1

- Modelo: gpt-6-luna (medium); commits `257547e` e `a3c2013`.
- Evidência: `aggregateClosedHours` chama sempre `createAverage`, mesmo se já existir média para o grupo; uma leitura bruta tardia concorrente com a agregação pode ficar presa por violação de unicidade. O design exige que `applyGroup` funda médias existentes ou descarte registros editados/excluídos. A revisão de T22 também identificou ausência de transação/bloqueio único na mesclagem, com risco de atualização perdida.
- Resultado: integração não aprovada; correção com gpt-6.1-sol (medium) pendente; tasks dependentes de agregação bloqueadas até verificação.
- Repetição gpt-6.1-sol: commit `c197dec`, 28 testes passaram; verificador reprovou por identificar que edição humana pode trocar tipo pH RAW/TREATED, alterando a identidade usada na consulta de janela. Leitura atrasada pode recriar média editada. Correção adicional necessária.
- Tentativa de gate da repetição executada inicialmente na raiz sem pom.xml; corrigida executando em backend, gate passou.
- Segunda repetição gpt-6.1-sol: commit `8995871` preserva tipo pH original com migração e testes. Gate de 62 testes e verificação independente passaram. Limite: migração não verificada em PostgreSQL real (T54).
- Durante a segunda repetição, teste de serviço falhou por stub Mockito de overload incorreto; corrigido e gate repetido com sucesso.

## 2026-11-13 — T24, tentativa 1

- Modelo: gpt-6-luna (medium); sem commit de T24.
- Evidência: gate falhou em testCompile: `PhAuthorshipRepositoryTest` chama método antigo `findByDeviceKeyAndTypeAndDeviceWindowEnd`, renomeado na correção `8995871`; três testes de T24 não chegaram a executar.
- Resultado: repetição com gpt-6.1-sol pendente para corrigir compatibilidade do teste e completar T24.
- Repetição gpt-6.1-sol: commits `c24c34f` e `533eb16`, gates passaram; verificador reprovou por ausência de asserção explícita do log no teste de exceção, embora código registre e log tenha aparecido. Nova repetição pendente.
- Segunda repetição `c41e8b5`: asserção do log e exceção adicionada; verificador independente aprovou, 10 testes passaram.

## 2026-11-13 — T25, tentativa 1

- Modelo: gpt-6-luna (medium), commit `b7c2ccd`; oito testes passaram.
- Verificador reprovou: criação concorrente de mesmo nome/ETE pode violar constraint após pré-checagem e resultar em HTTP 500 em vez de 409. Evidência estática; restrição impede duplicação.
- Resultado: repetição com gpt-6.1-sol pendente; lotes dependentes bloqueados.
- Repetição gpt-6.1-sol `e4c0bd2`: flush e tradução específica da constraint; verificador aprovou 29 testes, incluindo concorrência e rollback H2. PostgreSQL real pendente T54.

## 2026-11-13 — T26/T27, tentativa 1

- Modelo: gpt-6-luna (medium); commits `195bdea`, `ea9a2c8`. Gate unitário passou (16 testes). Uma compilação intermediária falhou por import ausente de `@Modifying` e foi corrigida antes do commit.
- Verificador independente não conseguiu comprovar persistência/rollback: teste temporário falhou ao inicializar o contexto de teste; análise estática confirmou transação e ordenação. Resultado inconclusivo, não aprovado.
- Repetição gpt-6.1-sol pendente para teste de integração e eventual correção.
- Repetição `be7aa39` gpt-6.1-sol: testes H2 de persistência e rollback adicionados; verificador aprovou, 19 testes passaram. Isolamento por outra chave conferido estaticamente, sem fixture adicional nesse teste.

## 2026-11-13 — T28, tentativa 1

- Modelo gpt-6-luna (medium), commit `d0e662d`; primeiro gate falhou por contexto de teste sem SecurityConfig; configurado e 12 testes passaram.
- Verificador reprovou: testes `@WithMockUser` usam principal incompatível com `UserPrincipal`, resolvido como null e aceito pelo mock; PATCH não confere encaminhamento do estado solicitado.
- Resultado: repetição com gpt-6.1-sol pendente; próximas tasks dependentes bloqueadas.
- Repetição `dd5b43e` gpt-6.1-sol: testes usam UserPrincipal, verificam argumentos e PATCH; verificador aprovou 12 testes.

## 2026-11-13 — T31, tentativa 1

- Modelo gpt-6-luna (medium), sem commit. Suíte direcionada de segurança passou, mas gate completo falhou: após ajustes, 462 testes, 2 falhas em DeviceReadingRepositoryTest e 20 erros de contexto por dependências ausentes de DeviceKeyRepository/AuditService.
- Resultado: gate não aprovado; repetir com gpt-6.1-sol e investigar isolamento e contextos antes de avançar.
- Repetição gpt-6.1-sol `71bb274`/`23db4e0`: contextos H2/teste isolados, resíduo compilado removido por clean, regras testadas com endpoints de prova. Verificador aprovou gate completo: 464 testes, sem falhas.

## 2026-11-13 — T32, tentativa 1

- Modelo gpt-6-luna (medium), commit `561df5e`; gate passou 5 testes; duas chamadas iniciais usaram diretório incorreto e foram corrigidas.
- Verificador reprovou: mapa de estados por IP nunca remove entradas expiradas, permitindo crescimento ilimitado. Também apontou que chamadas de verificação e registro separadas podem disputar sob concorrência; integração será T33.
- Resultado: repetição gpt-6.1-sol pendente; login bloqueado até aprovação.
- Repetição `db49cfd` gpt-6.1-sol: retenção limitada/expiração e testes de carga; verificador aprovou 13 testes. Coordenação da sequência por IP necessária em T33.

## 2026-11-13 — T33, tentativa 1

- Modelo gpt-6-luna (medium), commit `62fa120`. Gate passou 6 testes após correção de stubs Mockito desnecessários no primeiro teste.
- Verificador reprovou por falta de testes de sucesso atrasado concorrendo com bloqueio e IPs distintos durante I/O; observou transação iniciada antes da checagem de bloqueio, podendo adquirir conexão embora repositório não seja consultado.
- Resultado: repetição gpt-6.1-sol pendente.
- Repetição `1a15367` gpt-6.1-sol: transação adiada e 23 testes passaram, mas verificador reprovou porque DeviceLoginThrottle não é bean Spring e o teste fornece bean manualmente, impedindo subida real da aplicação. Nova repetição pendente.
