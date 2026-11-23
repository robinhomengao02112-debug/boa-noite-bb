# Starter: motor de jogo interativo para TikTok LIVE

Protótipo funcional do jogo **"Corrida de Elementos"** (detalhes no arquivo `05-ideias-de-jogos.md`, item #2).

## O que já faz
- Conecta na sua live (biblioteca `tiktok-live-connector` 2.5.0) e lê presentes, comentários, curtidas, seguidas, compartilhamentos e entradas. **Só lê**: não envia nada em nome da conta.
- Conta presentes em combo do jeito certo (só o evento final, com `repeatEnd`).
- Roda o jogo no servidor, então recarregar o overlay não perde o placar.
- Overlay vertical 1080x1920 para OBS ou LIVE Studio (Fonte de navegador).
- **Simulador**: testa tudo sem estar ao vivo.
- **Registro de receita**: `data/ledger-AAAA-MM-DD.jsonl` + `GET /api/stats`.
- **Saúde**: `GET /health`.
- **Supervisor**: reinicia se cair ou travar e avisa se ficar sem eventos.
- Reconexão automática com espera crescente se a live cair.

## Como rodar (Windows)
1. Instale o **Node.js 20 ou mais novo** (LTS) em nodejs.org.
2. Abra `config.json` e troque `SEU_USUARIO_AQUI` pelo seu @ (sem o @).
3. **Teste sem live:** dê dois cliques em `scripts\simular-windows.bat`. O navegador abre com o jogo rodando com eventos falsos.
4. **Ao vivo:** comece a live pelo LIVE Studio ou OBS e depois rode `scripts\iniciar-windows.bat`.
5. No OBS ou LIVE Studio: **Fonte de navegador** → URL `http://localhost:3000/`, largura 1080, altura 1920.

Pelo terminal (qualquer sistema):
```bash
npm install
npm run sim          # simulador
npm start            # conexão real
npm run supervisor   # conexão real com auto-reinício
```

## Ajustes (config.json)
| Campo | Para que serve |
|---|---|
| `teams` | Nome, cor e emoji dos times |
| `round.trackLength / maxSeconds / intermissionSeconds` | Tamanho da pista, duração máxima da rodada e intervalo |
| `scoring.*` | Pontos por moeda de presente, curtidas, comentário, seguir e compartilhar |
| `specialGifts` | Nome exato do presente → efeito (`boost`, `shield`, `freeze_leader`, `mega`) |
| `eulerApiKey` | Chave grátis da Euler Stream (opcional; aumenta os limites) |
| `estimatedUsdPerDiamond` | Taxa usada na estimativa de receita (padrão 0,005) |

## Estrutura
```
src/server.js            servidor HTTP + socket.io + registro + saúde
src/game.js              regras do jogo (troque este arquivo para fazer outro jogo)
src/sources/tiktok.js    conexão real com a live
src/sources/simulator.js eventos falsos para teste
public/index.html        overlay (o que aparece na live)
scripts/supervisor.js    vigia 24h
scripts/*.bat            atalhos para Windows
data/                    registros (não vão para o git)
```

## Para criar outro jogo
Mantenha `server.js` e `sources/`. Crie um novo `game.js` com os mesmos métodos (`handle(ev)`, `tick(dt)`, `snapshot()`) e um novo `public/index.html`. Todos os eventos chegam no mesmo formato:
```js
{ type: 'gift',   user: { id, nick, avatar }, giftName, diamonds, count, totalDiamonds }
{ type: 'chat',   user, text }
{ type: 'like',   user, count }
{ type: 'follow', user } | { type: 'share', user } | { type: 'join', user }
```
