# Starter: motor de jogos interativos para TikTok LIVE

## Jogos incluídos (todos jogáveis)
| id | Jogo | Estilo |
|---|---|---|
| `chefao` | **Raid do Chefão**: o chat inteiro ataca um chefe, que fica mais forte a cada nível | Cooperativo |
| `corrida` | **Corrida de Elementos**: 4 times correm; comentar 1–4 escolhe o time | Times |
| `melancia` | **Estoura ou Não**: elásticos até estourar; o limite é escondido | Suspense |

As fichas de cada jogo e de mais 42 ideias estão em `../10-catalogo-de-jogos.md`.

## O que o motor faz
- Conecta na sua live (`tiktok-live-connector` 2.5.0) e **só lê** os eventos: presentes, comentários, curtidas, seguidas, compartilhamentos e entradas.
- Conta presentes em combo do jeito certo (só o evento final, com `repeatEnd`).
- Faz **rodízio automático** entre jogos (sempre entre rodadas) **sem mudar a URL do OBS**: `http://localhost:3000/` sempre mostra o jogo ativo.
- **Simulador** para testar sem estar ao vivo.
- **Receita estimada** por dia, **por jogo** e **por hora**: `GET /api/stats`.
- Página de saúde em `GET /health`. O **supervisor** reinicia o servidor se cair ou travar.

## Como rodar (Windows)
1. Instale o **Node.js 20 ou mais novo** (LTS) em nodejs.org.
2. Em `config.json`, troque `SEU_USUARIO_AQUI` pelo seu @ (sem o @).
3. **Teste sem live:** `scripts\simular-windows.bat`.
4. **Ao vivo:** comece a live e depois rode `scripts\iniciar-windows.bat`.
5. No OBS ou LIVE Studio: **Fonte de navegador** → `http://localhost:3000/`, 1080x1920.

Pelo terminal:
```bash
npm install
npm run sim                                  # simulador com o jogo de config.json
node src/server.js --simulate --game=melancia  # simulador com um jogo específico
npm start                                    # conexão real
npm run supervisor                           # conexão real com auto-reinício
```

**Trocar de jogo na hora** (só pelo próprio PC): abra `http://localhost:3000/api/game/corrida`, `/api/game/chefao` ou `/api/game/melancia`.

## config.json
| Campo | Para que serve |
|---|---|
| `activeGame` | Jogo inicial |
| `rotation.enabled / games / minutesPerGame` | Rodízio automático entre jogos |
| `games.corrida` | Times, duração, pontos e efeitos por presente (nome exato do presente → `boost`, `shield`, `freeze_leader`, `mega`) |
| `games.chefao` | Vida inicial, crescimento por nível, dano por ação, faixas de presente (Ataque/Raio/Meteoro/ULTIMATE), lista de chefes |
| `games.melancia` | Faixa do limite escondido, elásticos por ação, objetos |
| `eulerApiKey` | Chave grátis da Euler Stream (opcional; aumenta os limites) |
| `estimatedUsdPerDiamond` | Taxa da estimativa de receita (padrão 0,005) |

## Estrutura
```
src/server.js            servidor, rodízio, registro de receita, saúde
src/games/index.js       registro dos jogos
src/games/<id>.js        regras de cada jogo
src/sources/tiktok.js    conexão real com a live
src/sources/simulator.js eventos falsos para teste
public/<id>/index.html   overlay de cada jogo
public/shared/client.js  código comum dos overlays (troca automática de jogo, status)
scripts/supervisor.js    vigia 24h
scripts/*.bat            atalhos para Windows
```

## Criar um jogo novo
1. Crie `src/games/<id>.js` com uma classe que tenha `static id`, `constructor(cfg)`, `publicConfig()`, `handle(ev)` (que devolve um efeito ou `null`), `tick(dt)` e `snapshot()`. O campo `phase` do snapshot deve ser `intermission`, `defeated` ou `exploded` entre rodadas, para o rodízio saber quando pode trocar.
2. Crie `public/<id>/index.html` incluindo `/socket.io/socket.io.js` e `/shared/client.js`.
3. Adicione `games.<id>` em `config.json` e registre o jogo em `src/games/index.js`.

Formato dos eventos que chegam em `handle(ev)`:
```js
{ type: 'gift',   user: { id, nick, avatar }, giftName, diamonds, count, totalDiamonds }
{ type: 'chat',   user, text }
{ type: 'like',   user, count }
{ type: 'follow', user } | { type: 'share', user } | { type: 'join', user }
```
