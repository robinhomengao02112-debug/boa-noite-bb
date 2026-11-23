# Starter: sistema da live automática (jogos + controlador + OBS + aprendizado)

> **Para instalar no Windows, use o `INSTALAR.bat` da pasta de cima e siga o `COMO-USAR.md`.** Este arquivo é a referência técnica.

## Peças
| Peça | Arquivo | O que faz |
|---|---|---|
| **Controlador** | `src/controller.js` | O piloto automático. Mantém o jogo e o OBS de pé, configura o OBS, liga e desliga a transmissão, religa após quedas, desliga no horário marcado e serve o **painel** em `http://127.0.0.1:3001` |
| Controle do OBS | `src/obs.js` | Fala com o OBS pelo obs-websocket: cria o perfil vertical, a cena e a fonte; define a chave; inicia e para; limpa a "marca de queda" para não abrir o Modo de Segurança |
| Servidor do jogo | `src/server.js` | Lê os eventos da live, roda o jogo ativo, faz o rodízio (simples ou **inteligente**), registra a receita e o **aprendizado** |
| Jogos | `src/games/*.js` + `public/<id>/` | `chefao`, `corrida`, `melancia` |
| Fontes de eventos | `src/sources/tiktok.js`, `simulator.js` | Live real (`tiktok-live-connector` 2.5.0, só leitura) ou simulador |
| Painel | `painel/index.html` | Botões LIGAR/DESLIGAR, chave, jogos, rodízio, aprendizado e registro |
| Configuração inicial | `scripts/setup.js` | Usuário, senha do obs-websocket e opções do OBS que evitam janelas travando |
| Windows | `scripts/instalar.ps1`, `iniciar-oculto.vbs`, `ligar.bat`, `desligar.bat`, `parar-tudo.bat`, `simular-windows.bat` | Instalação, início escondido e atalhos |

## Comandos (terminal)
```bash
npm ci --omit=dev
node scripts/setup.js --user=seu_usuario   # com o OBS FECHADO
npm run controlador                        # piloto automático + painel (http://127.0.0.1:3001)
npm run controlador:sim                    # o mesmo, com eventos simulados
npm run sim                                # só o jogo, simulado (http://127.0.0.1:3000)
```

## API do painel (local, 127.0.0.1:3001)
| Método | Rota | Corpo |
|---|---|---|
| GET | `/api/status` | — |
| POST | `/api/ligar` | `{ server?, key?, autoStopAt? "HH:MM" }` (vazio = usa a última chave) |
| POST | `/api/desligar` | — |
| POST | `/api/modo` | `{ mode: "obs" \| "studio" }` |
| POST | `/api/jogo` | `{ id }` |
| POST | `/api/config` | `{ user?, activeGame?, rotationEnabled?, smart?, minutesPerGame? }` |

Servidor do jogo (127.0.0.1:3000): `/health`, `/api/stats?day=`, `/api/aprendizado`, `/api/game/:id`.

## config.json
| Campo | Para que serve |
|---|---|
| `tiktokUsername` | Seu @ (sem @) |
| `obs.*` | Porta e senha do websocket (preenchidas pelo setup), nomes de perfil, coleção, cena e fonte, resolução (base 1080x1920, saída 720x1280, 30 fps), bitrate, `browserHwAccel` (padrão `false` = sem tela preta) |
| `rotation.enabled / smart / minutesPerGame` | Rodízio ligado, inteligente (aprende) ou em ordem, e minutos por jogo |
| `rotation.learnMinMinutes / exploreChance` | Minutos mínimos de teste por jogo antes de comparar (30), e chance de testar um jogo qualquer (0,2) |
| `games.<id>` | Ajustes de cada jogo |

## O que foi testado (OBS Studio 32.2 real, Linux, tela virtual)
- Configuração do zero: `setup.js` → controlador → OBS abre **sem assistente e sem nenhuma janela**, e cria o perfil, a cena e a fonte sozinho.
- LIGAR pelo painel → transmissão RTMP recebida (H.264 720x1280, 30 fps, AAC, **0 quadros perdidos**) com o jogo visível no vídeo.
- OBS derrubado no meio da live → reaberto **sem a janela de Modo de Segurança** → de volta ao ar em cerca de 20 s.
- Jogo derrubado → reiniciado. "PC reiniciado" (tudo derrubado) → controlador voltou ao ar sozinho.
- DESLIGAR e desligamento por horário.
- Segundo controlador aberto por engano → sai sem atrapalhar.
- Rodízio inteligente → testa todos os jogos e depois prioriza os de mais moedas por minuto.

**Não testado aqui:** o Windows em si (os scripts `.ps1`, `.bat` e `.vbs`) e a chave real do TikTok. Os pontos que dependem do Windows estão marcados no `COMO-USAR.md`.

## Criar um jogo novo
1. `src/games/<id>.js` com `static id`, `constructor(cfg)`, `publicConfig()`, `handle(ev)` (devolve um efeito ou `null`), `tick(dt)` e `snapshot()`. O `phase` do snapshot deve ser `intermission`, `defeated` ou `exploded` entre rodadas, para o rodízio saber quando trocar.
2. `public/<id>/index.html` incluindo `/socket.io/socket.io.js` e `/shared/client.js`.
3. Registre em `src/games/index.js` e adicione `games.<id>` no `config.json` e em `rotation.games`.

Formato dos eventos:
```js
{ type: 'gift',   user: { id, nick, avatar }, giftName, diamonds, count, totalDiamonds }
{ type: 'chat',   user, text }
{ type: 'like',   user, count }
{ type: 'follow', user } | { type: 'share', user } | { type: 'join', user }
```
