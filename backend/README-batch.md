# Coletas em lote

`POST /api/device/readings/batch` exige Bearer DEVICE e aceita `{"readings":[...]}` com 1 a 100 amostras. Cada amostra tem `id` estável de 1 a 64 caracteres ASCII alfanuméricos, `_` ou `-`, `timeEstimated` obrigatório e os campos opcionais `heightCm`, `rawPh`, `treatedPh` e `measuredAt` (instante ISO-8601). Altura deve estar entre 0 e 45 cm e pH entre 0 e 14, com até duas casas decimais. Horários futuros são inválidos. Se `measuredAt` for omitido, `timeEstimated` deve ser `true` e a hora de recebimento é usada.

A resposta contém um resultado por ID com `accepted`, `rejected`, `discarded` e `duplicate`. O dispositivo deve manter amostras sem confirmação; repetir um ID para o mesmo dispositivo retorna o resultado original, sem processá-lo novamente. IDs repetidos no mesmo lote são inválidos. O lote não usa o limite de frequência da rota individual. Leituras com mais de 48 horas, ou que conflitem com uma média editada ou excluída, são descartadas com motivo explícito.

Horários estimados informados pela placa usam origem `ELAPSED_TIME`; quando a hora é atribuída no recebimento, `RECEIVED_AT`. Médias com ambas as origens usam `MIXED`. Medições confiáveis ou manuais usam `timeEstimated: false` e origem nula.

O Flyway cria o esquema em bancos vazios com V1; em banco legado não vazio, registra a baseline 1 e aplica V2. A inicialização passa a validar o esquema em vez de alterá-lo automaticamente. Confirme a integridade do banco legado e faça backup antes da atualização.
