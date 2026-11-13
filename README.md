# Sistema de Gestao ETE

Sistema web para coletar, centralizar e acompanhar dados operacionais das estacoes de tratamento de agua da Carbonifera Catarinense.

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
- As estacoes iniciais sao `ETE Novo Horizonte` e `ETE Boa Vista`.
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
