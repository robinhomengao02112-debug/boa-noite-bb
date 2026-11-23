# 06 — Ferramentas, APIs e jogos prontos do mercado

> Os preços e números abaixo vêm dos sites de cada fornecedor (lidos em 23/11/2026). **Números de "sucesso" são propaganda do próprio fornecedor.** Nenhuma dessas empresas é afiliada ao TikTok.

## 6.1 Conexão com a live (para programar o próprio jogo)
| Ferramenta | Tipo | Preço | Observações |
|---|---|---|---|
| **tiktok-live-connector** (npm) | Biblioteca Node.js, código aberto | Grátis | v2.5.0 (16/09/2026). "Unofficial, Unaffiliated with ByteDance". Usada no `starter/`. |
| **TikTokLive** (PyPI, isaackogan) | Biblioteca Python, código aberto | Grátis | v7.0.1 (10/09/2026). Licença AGPL modificada. Cerca de 1,7 mil estrelas no GitHub. |
| **Euler Stream** | Servidor de assinatura e API gerenciada | Grátis: 1,5 mil requisições/dia, 100 salas/dia, 25 WebSockets. Pagos: US$ 50 / 100 / 150 / 250 por mês | Usado pelas duas bibliotecas. O plano grátis basta para uma live. |
| **TikTool (tik.tools)** | API WebSocket gerenciada + SDK `@tiktool/live` | Sandbox de avaliação; planos pagos | Tem guia para Unity, C++ e Web |
| **BetterTok** | API WebSocket, só leitura | Ver site | Alternativa |

## 6.2 Ferramentas sem programação
| Ferramenta | O que faz | Preço |
|---|---|---|
| **TikFinity** (Zerody) | Alertas, TTS, overlays, metas, chatbot, pedidos de música, **Actions & Events** (presente → ação), **simulação de teclas** (controlar programas do Windows com presentes), Minecraft, GTA 5 (plugin), Streamer.bot, Voicemod | Ferramentas básicas grátis. Pro ≈ US$ 19/mês, segundo fontes de terceiros. Funciona no OBS e no LIVE Studio. |
| **Streamer.bot** | Automação avançada (scripts, ações encadeadas) | Grátis |
| **VDO.Ninja** | Câmera remota → OBS (para o apresentador em casa) | Grátis |
| **TikTok LIVE Studio** (oficial) | Transmissão + widgets oficiais (alertas, metas, Gift Gallery, ranking, contagem que estende) | Grátis |
| **OBS Studio** | Transmissão com automação (linha de comando, obs-websocket) | Grátis |

## 6.3 Jogos prontos (para testar formatos antes de programar)
| Fornecedor | Preço | Jogos / mecânicas (exemplos) |
|---|---|---|
| **Interactive Live** (interactivelivegames.com, **brasileiro**) | Grátis (sem jogos); **PRO US$ 5,99/mês** (20 jogos); **MAX US$ 9,99/mês** (34 jogos, mod de Minecraft, Roblox) | Guerra de Território (times ou países), Pipa, Torre vs Torre, Melancia de Elástico, Adivinhe o Desenho, STOP, Cobra nas Nuvens, Batalha Naval, Copa Brasil, X1 Cup. Também tem jogos políticos e Plinko, que **evitamos** (arquivo 05). |
| **TikLive Games / TikPilot** (tiklivegames.io) | A partir de **US$ 13,99/mês por jogo** | Animal Run (países), Crowd Race (até 100 corredores), Grand Prix, Hill Rush, Team Clash, Push Battle, Circle Clash, Pênalti, Dead Run (zumbis), jogos de terror |
| **TikXander** | "A partir de cerca de US$ 2"; sem plano grátis | Tower of God, Cube vs Cube, Crowd Army, Parkour, Glory Race, Subway Surf. Diz ter "25 milhões de presentes em 27 mil lives" (propaganda do fornecedor). |
| **TiktokBoxGame** (tiktokgames.live) | Ver site | Getting Over It interativo, Spawn Wars (StarCraft 2), Dota 2 interativo. Diz ter "dados de mais de 1.500 streamers". |
| **itch.io** (tags *tiktok-game*) | Vários, pagamento único (ex.: Country Race ≈ €12,99) | Corridas de bolinhas, territórios, times |
| **vamnguyen/tiktok-live-games** (GitHub) | Grátis (MIT) | Corrida de cavalos por país. É um bom exemplo de código. |

**Estratégia sugerida:** gaste 1 mês de US$ 6–10 num pacote de jogos (o Interactive Live é brasileiro e barato) para **testar quais formatos o seu público responde melhor**. Depois construa a **sua** versão própria e melhor no nosso motor. Ser dono do jogo traz diferencial, sem custo mensal e com a chance de vender depois.

## 6.4 Comparação: comprar pronto x construir

| | Comprar pronto | Construir (nosso `starter/`) |
|---|---|---|
| Velocidade | Imediato | Dias ou semanas por jogo |
| Custo | US$ 6–14/mês por jogo ou pacote | Grátis (o seu tempo e o meu) |
| Diferenciação | Baixa: muitos streamers usam o mesmo | **Alta: jogo exclusivo** |
| Controle (regras, visual, idioma) | Limitado | Total |
| Dados de receita por horário | Não | **Sim** (registro por presente) |
| Risco de o fornecedor sair do ar | Existe | Depende só da biblioteca de conexão |
