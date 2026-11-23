# 05 — Ideias de jogos para lucrar (com análise)

## 5.1 O que faz um jogo de live dar dinheiro (padrões do mercado)
O mercado tem dezenas de jogos vendidos, e os mais citados repetem os mesmos padrões:
corridas por países ou times, batalhas de território, cabo de guerra, exércitos que nascem de presentes, jogos de "ajuda ou atrapalha" o streamer, Minecraft com presentes, adivinhação de palavras e desenho, e cobra que cresce. Detalhes no arquivo 06.

**Os 7 ingredientes que se repetem:**
1. **Participação grátis primeiro.** Comentar ou curtir já põe a pessoa no jogo. O presente é um "turbo", não a entrada.
2. **Nome na tela.** Quem ajuda aparece no placar, no feed ou no próprio personagem. Reconhecimento é o principal motivo para dar presente.
3. **Efeito proporcional e visível.** Uma rosa causa um efeito pequeno; uma Galaxy causa um efeito enorme e espetacular.
4. **Rodadas curtas (2–5 min) com virada possível.** Sempre há chance de reação, o que gera suspense.
5. **Times ou comunidade.** As pessoas defendem o próprio grupo: país, estado, time, elemento.
6. **Tela nunca parada.** Mesmo sem eventos, algo se mexe (o "modo atração"), o que atende à regra contra "static screens".
7. **Progresso que fica.** Vitórias do dia, ranking semanal e mundo persistente fazem a pessoa voltar.

## 5.2 Critérios de nota (0 a 5)
- **Dinheiro:** potencial de presentes.
- **Sozinho:** funciona sem alguém jogando (o apresentador só narra).
- **Pouco público:** funciona bem com 5–30 pessoas, a realidade do começo.
- **Regras:** quanto maior, mais seguro diante das diretrizes.
- **Esforço:** quanto maior, mais fácil de desenvolver.

## 5.3 Tabela de avaliação
O número (#) só identifica o jogo e não é a posição. A coluna **Total** é a nota final.

| # | Jogo | Dinheiro | Sozinho | Pouco público | Regras | Esforço | **Total** |
|---|---|---|---|---|---|---|---|
| 1 | **Raid Cooperativo: Chefão** | 4 | 5 | 5 | 5 | 4 | **23** |
| 2 | **Corrida de Times** (pronto no `starter/`) | 4 | 5 | 4 | 4 | 5 | **22** |
| 3 | **Batalha dos Estados** (mapa do Brasil) | 5 | 5 | 3 | 4 | 3 | **20** |
| 4 | **Exército de Nomes** (arena) | 5 | 5 | 3 | 4 | 3 | **20** |
| 5 | **Aquário/Fazenda Coletiva** (mundo persistente) | 4 | 5 | 4 | 4 | 3 | **20** |
| 6 | **Torre Mais Alta** (empilhar vs derrubar) | 4 | 5 | 3 | 4 | 3 | **19** |
| 7 | **Cabo de Guerra** | 4 | 5 | 3 | 3 | 5 | **20** |
| 8 | **Quiz Relâmpago** | 2 | 5 | 4 | 5 | 4 | **20** |
| 9 | **Palavra Secreta** (forca/adivinhação) | 3 | 5 | 4 | 5 | 4 | **21** |
| 10 | **Cobra Gigante** | 3 | 5 | 4 | 4 | 4 | **20** |
| 11 | **Melancia/Balão que estoura** (suspense) | 3 | 5 | 4 | 4 | 5 | **21** |
| 12 | **Ajuda ou Atrapalha** (apresentador joga plataforma) | 5 | 0 | 4 | 4 | 3 | só em blocos com apresentador |
| 13 | **Minecraft com presentes** (via TikFinity) | 5 | 0 | 4 | 4 | 4 | só em blocos com apresentador |
| 14 | **Desafio do Apresentador** (metas que destravam ações) | 4 | 0 | 4 | 4 | 5 | só em blocos com apresentador |
| 15 | **Maratona com relógio** (contagem que estende) | 3 | 5 | 4 | 4 | 5 | **21** (camada extra, combina com todos) |
| 16 | **Liga Semanal** (temporada entre jogos) | 4 | 5 | 4 | 5 | 4 | **22** (camada extra, combina com todos) |

**Recomendação:**
- **MVP (agora):** #2 Corrida de Times, que já está pronto, com a camada **#16 Liga Semanal**.
- **Versão 2:** #1 **Raid Cooperativo**. Funciona com pouca gente, não divide o público e todo presente "acerta o chefe".
- **Versão 3 (o maior potencial no Brasil):** #3 **Batalha dos Estados**. A rivalidade regional é saudável e o público se identifica.
- **Nos blocos com apresentador:** #12 ou #13, que são os que mais rendem quando há alguém jogando de verdade.

## 5.4 Fichas detalhadas dos principais

### #1 Raid Cooperativo: "Chefão"
**Ideia:** um chefe gigante com barra de vida aparece. **Todo o chat ataca junto.** Quando o chefe morre, vem outro maior (nível +1), com visual novo a cada 5 níveis.
| Ação | Efeito |
|---|---|
| Comentar | Entra na raid; o personagem com o seu nome aparece atacando |
| Curtidas | 1 de dano a cada 10 curtidas |
| Seguir / Compartilhar | Golpe crítico (+25 / +40) |
| Presente pequeno (1–5 moedas) | Ataque com o seu nome em destaque |
| Presente médio (30–100) | Habilidade especial: raio, meteoro |
| Presente grande (1.000+) | "Ultimate": animação de tela cheia + golpe final com o seu nome |
| Fim do chefe | Ranking de dano (top 3) + "último golpe por @nome" |

**Por que funciona:** todos jogam juntos, então não há "lados" (evita a regra de "pontos de vista opostos"). Funciona com 3 pessoas. "Último golpe" e "maior dano" disparam a competição pelo reconhecimento.

### #2 Corrida de Times: "Corrida de Elementos" (implementada)
- 4 times: Fogo, Água, Raio e Floresta. Comente **1–4** para entrar; quem não escolher entra no time menor.
- Comentário: +0,5 (com espera de 5 s por pessoa contra spam). 10 curtidas: +1. Seguir: +5. Compartilhar: +8. Presente: **4 pontos por moeda**.
- **Efeitos especiais** (configuráveis em `config.json`): Rose = turbo; Finger Heart = escudo de 10 s; Doughnut = congela o líder por 5 s; Galaxy = MEGA turbo.
- Rodada de até 4 min com 20 s de intervalo; vitórias do dia; destaques da rodada.

### #3 Batalha dos Estados
- Mapa do Brasil em grade de pixels. Comente a **sigla do seu estado** (SP, BA, MG…) para entrar.
- Cada ponto pinta pixels a partir das fronteiras do estado, que avança sobre os vizinhos.
- Presentes grandes viram "bomba de tinta" com o nome de quem enviou.
- **Placar do dia por estado** e "estado campeão da semana".
- **Cuidado:** manter o clima de brincadeira e moderar xingamentos regionais. Não use bandeiras ou símbolos de partidos e nada de política.

### #4 Exército de Nomes (arena)
- Cada participante vira um soldadinho com o próprio nome. Presentes criam unidades maiores (cavaleiro → gigante → dragão).
- Dois a quatro exércitos lutam sozinhos; o último de pé vence a rodada.
- É o formato "spawn wars", entre os mais citados como campeões de presentes.

### #5 Aquário/Fazenda Coletiva (mundo persistente)
- Cada presente coloca **um peixe ou animal com o seu nome** num aquário que **continua lá nos outros dias**.
- Presentes maiores trazem espécies raras. Curtidas alimentam o aquário inteiro.
- O motivo para voltar é ver o próprio peixe crescendo.
- **Nunca** faça "o peixe morre se você não mandar presente". Isso é pressão emocional e cai na regra de "sympathy/obligation".

### #11 Melancia ou balão que estoura
- Cada presente adiciona elásticos ou ar. Ninguém sabe exatamente quando estoura (há um limite escondido).
- Quem causa o estouro ganha o destaque "EXPLODIU por @nome".
- É simples, tem suspense, rende muitas rosas e é fácil de fazer.
- **Atenção:** é aleatório, mas **não vale prêmio nenhum**, só reconhecimento. Mantenha assim.

### #12 Ajuda ou Atrapalha (com apresentador)
- O apresentador joga um jogo de plataforma ou escalada difícil, que pode ser **feito por nós** para não ter problema de licença.
- Presentes de "ajuda" dão pulo extra ou checkpoint; presentes de "atrapalha" trazem vento, empurrão ou inimigo com o nome de quem mandou.
- O que gera presentes é a **reação humana** do apresentador. Por isso só roda nos blocos com alguém presente.

### #13 Minecraft com presentes (com apresentador)
- Feito sem programar, via **TikFinity → Actions & Events** e um conector de Minecraft (TikfiniTok ou ServerTap).
- Exemplo de mapeamento: Rose = soco; Finger Heart = prisão de 5 s; seguir = 3 zumbis; presente de 299 moedas = volta 5 níveis (exemplo de um vendedor brasileiro).
- Use mapas e mods com licença que permita uso em live.

### Camadas que combinam com todos
- **#15 Maratona com relógio:** "Extendable countdown", ferramenta oficial do LIVE Studio. Presentes e assinaturas estendem a live.
- **#16 Liga Semanal:** pontos de todas as rodadas somam num ranking da semana. "Campeão da semana" com destaque fixo no overlay. Faz a pessoa voltar.

## 5.5 Formatos a EVITAR
| Formato | Por quê |
|---|---|
| Roleta, sorteio, plinko ou "caixa surpresa" **valendo prêmio** (dinheiro, Pix, produto, moedas) | Jogo de azar: proibido pelo TikTok ("Do not promote gambling"; política contra "lucky draws, spin wheels") e contravenção penal no Brasil |
| "Quem é melhor, A ou B? Mande presente" **sem jogo de verdade** | A regra cita "two or more opposing viewpoints and ask viewers to send Gifts" |
| Política: eleição, candidatos, "Lula x Flávio" (existe no mercado) | Conteúdo polarizador desmonetiza; contas políticas nem monetizam |
| Pet ou personagem que **sofre ou morre** sem presentes | Exploração de "emotions, sympathy, or sense of obligation" |
| "Siga que eu sigo de volta", "presente em troca de seguida" | "follow-for-follow" e trocas artificiais |
| Repetir "mandem presente" o tempo todo (por voz, TTS ou texto) | "mechanically and repeatedly solicits" |
| Gravação ou loop de jogo passando como ao vivo | Conteúdo pré-gravado |
| Jogos comerciais, músicas ou vídeos sem licença para live | Direitos autorais |
| QR code, link ou @ de outra plataforma na tela | Proibido nas diretrizes de LIVE |

## 5.6 Como redigir o texto na tela (exemplos seguros)
- ✅ "Comente 1, 2, 3 ou 4 para entrar num time!"
- ✅ "Curtir e comentar empurram seu time. Presentes dão turbos!"
- ✅ "@ana deu um MEGA TURBO no time Fogo!"
- ❌ "MANDEM PRESENTES SENÃO O TIME PERDE!!!"
- ❌ "Quem mandar Galaxy ganha R$ 50 no Pix"
