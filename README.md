# Sistema de Gestao ETE

Sistema web para coletar, centralizar e acompanhar dados operacionais das estacoes de tratamento de agua.

## Estrutura

- `backend/`: API Spring Boot.
- `frontend/`: interface React com TypeScript.
- `docker-compose.yml`: banco PostgreSQL, backend e frontend.
- `.env.example`: variaveis esperadas pelo sistema.

## Ambiente

Copie `.env.example` para `.env` e ajuste os valores antes de rodar em ambiente real. O arquivo `.env` nao e versionado.

```powershell
Copy-Item .env.example .env
```

As variaveis sensiveis (`DB_PASSWORD`, `JWT_SECRET`, `INITIAL_ADMIN_PASSWORD`) devem ser alteradas. O backend falha a inicializacao quando placeholders do exemplo sao usados.

## Execucao com Docker

```powershell
docker compose up --build
```

Servicos padrao:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:6060`
- PostgreSQL: `localhost:5432`

## Decisoes iniciais

- O sistema suporta multiplas estacoes desde o inicio.
- As estacoes iniciais sao `Empresa 1` e `Empresa 2`.
- Dashboards e relatorios suportam filtro por estacao e visao consolidada.
- Turnos fixos: `05:00-17:00` e `17:00-05:00`.
- Timezone oficial: `America/Sao_Paulo`.
- Estoque atual de cal: ultimo snapshot de fim de turno + entradas - consumos posteriores.
- Paradas que cruzam turnos sao contabilizadas por turno.
- Notificacoes internas e Web Push usam o mesmo dispatcher; sem chaves VAPID o envio Web Push degrada para no-op logado.

## Web Push

Configure as chaves VAPID no backend antes de habilitar notificacoes no celular em producao:

- `PUSH_VAPID_PUBLIC_KEY`
- `PUSH_VAPID_PRIVATE_KEY`
- `PUSH_VAPID_SUBJECT` (ex.: `mailto:admin@empresa.com`)

As chaves podem ser geradas com a CLI da biblioteca Java (`generate-key`) ou com `web-push generate-vapid-keys`. Nao commite chaves reais. Em producao, o frontend precisa rodar em HTTPS; `localhost` e a excecao aceita pelos navegadores. O service worker e servido em `/sw.js` e deve ser revalidado sem cache agressivo.

## Implantacao e dispositivos Arduino

- Configure `DEVICE_KEY_ENCRYPTION_SECRET` com um segredo obrigatorio de pelo menos 32 caracteres. A troca desse segredo torna as chaves de dispositivo existentes ilegíveis: exclua-as e gere novas chaves.
- `DEVICE_OFFLINE_MINUTES` define o tempo em minutos sem contato para considerar um dispositivo offline (padrao: `5`).
- `DEVICE_RATE_LIMIT_PER_MINUTE` define o limite de requisicoes de leitura por dispositivo por minuto (padrao: `10`).
- Em producao, sirva a aplicacao por HTTPS usando um proxy reverso, inclusive para proteger a chave e o token do dispositivo em transito.
- O limitador de login usa o endereco retornado por `request.getRemoteAddr()`. Atras de um proxy, esse endereco pode ser o do proprio proxy, fazendo clientes compartilharem o bloqueio. Configure o proxy para repassar o IP real e o servidor para reconhece-lo com uma configuracao confiavel; o codigo do login nao le diretamente cabecalhos como `X-Forwarded-For`.

### Protocolo Arduino

O dispositivo deve enviar uma leitura por minuto. Primeiro, autentique-se em `POST /api/auth/device-login`, endpoint publico, com uma chave ativa de 24 caracteres:

```json
{"key": "string"}
```

Exemplo (substitua URL e chave):

```bash
curl -X POST https://ete.example.com/api/auth/device-login \
  -H 'Content-Type: application/json' \
  -d '{"key":"SUA_CHAVE_DE_24_CARACTERES"}'
```

A resposta bem-sucedida tem o formato `{"accessToken": string, "tokenType": string, "expiresInSeconds": number}`. O JWT tem subject `device:<id>` e claims `role=DEVICE`, `deviceKeyId`, `stationId` e `tokenType=access`. Nao ha refresh token: ao expirar o token ou receber `401`, refaca o login com a chave. O `401` de login e generico e identico para chave desconhecida, desativada ou malformada e para corpo sem `key`. Dez falhas em cinco minutos bloqueiam o IP por 15 minutos; enquanto bloqueado, o login responde `429` sem corpo informativo e sem revelar se a chave existe.

Os erros usam o formato `ApiError`: `{"timestamp": string, "status": number, "error": string, "message": string, "path": string, "fields": [{"field": string, "message": string}]}`. A resposta `429` do login nao traz corpo informativo.

Envie os dados para `POST /api/device/readings` com `Authorization: Bearer <accessToken>` de dispositivo (`DEVICE`). O corpo tem os campos opcionais abaixo; pelo menos uma leitura numerica valida deve ser enviada:

```text
{"heightCm"?: number, "rawPh"?: number, "treatedPh"?: number, "measuredAt"?: ISO-8601 instant}
```

- `heightCm`: altura em centimetros, de 0 a 45 inclusive, com no maximo duas casas decimais.
- `rawPh` e `treatedPh`: pH bruto e tratado, respectivamente, de 0 a 14 inclusive, com no maximo duas casas decimais.
- `measuredAt`: instante da medicao em ISO-8601, por exemplo `2026-01-01T12:00:00Z`; quando omitido, usa o horario de recebimento. Nao pode estar no futuro.

```bash
curl -X POST https://ete.example.com/api/device/readings \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer SEU_ACCESS_TOKEN' \
  -d '{"heightCm":12.34,"rawPh":7.20,"treatedPh":7.10}'
```

Uma resposta `200` tem o formato:

```text
{"accepted": string[], "rejected": [{"field": string, "reason": string}], "discarded": [{"field": string, "reason": "EDITED" | "DELETED" | "TOO_OLD"}]}
```

`accepted` lista campos validos que foram armazenados ou incorporados; `rejected` informa campos invalidos e seus motivos. `discarded` informa leituras tardias descartadas porque o registro foi editado (`EDITED`), excluido (`DELETED`) ou e antigo demais (`TOO_OLD`); campos descartados nao aparecem em `accepted`. O descarte mantem a resposta `200` e gera log. Uma leitura exatamente as 15:00:00 pertence a janela que comeca as 15:00, no fuso `America/Sao_Paulo`.

O envio retorna `400` se nao houver valor valido ou o horario estiver no futuro; `401` para token ou chave invalidos (refaca o login com a chave); `403` para usuario humano; e `429` quando o limite por dispositivo for excedido (padrao: 10 por minuto).
