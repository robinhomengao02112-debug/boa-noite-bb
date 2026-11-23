# 10 — Catálogo de jogos (formatado para implementar)

> 45 ideias em 8 estilos, todas inspiradas em formatos que **estão rodando no mercado agora**, no **mesmo formato de ficha**. Assim qualquer uma vira jogo no nosso motor (`starter/`) sem precisar reinventar nada.
> **3 já estão prontas e jogáveis:** Raid do Chefão, Corrida de Elementos e Estoura ou Não.
> Pesquisa de 23/11/2026. As fontes estão no fim do arquivo.

---

## Parte 1: o que o mercado mostra (e por que isso importa no desenho)

### Por que as pessoas dão presentes
Estudo da Ipsos encomendado pelo TikTok (jun–jul/2025, 500 pessoas nos EUA):
- **35%** dão presente para mostrar apreço pelo criador.
- **34%** para melhorar a experiência com **elementos interativos** e **se destacar na comunidade**.
- **32%** para interagir com a live ou com outros espectadores.
- **31%** para comemorar um marco ou evento.
- **68%** dos usuários já deram presente em LIVE.

**Tradução para o design:** o jogo precisa (1) mostrar o **nome** de quem ajudou, (2) causar um **efeito visível na hora** e (3) ter **marcos** para comemorar (chefe derrotado, recorde, vitória do time).

### Estilos que mais aparecem nos catálogos de 2026
Os fornecedores (TikLive Games com cerca de 150 jogos, ScrollPlay, BeetGames, TikXander, Interactive Live, itch.io) repetem os mesmos estilos:

| Estilo | Exemplos no mercado | Observação |
|---|---|---|
| Cabo de guerra e subida em dupla | Timmy's Tower, Scribble Hop, World Cup Tug 2026 | A ScrollPlay diz ser o "formato mais transmitido" na plataforma dela (dado do próprio fornecedor) |
| Corrida de times ou países | Animal Run, Country Race, Grand Prix, Crowd Race | Muito comum |
| Batalha de times / "batalha" | Team Clash, Tower Battle, Missile War, Punch Battle | Fornecedores dizem que gera o maior volume de presentes por minuto (propaganda) |
| Física e escalada com sabotagem | Getting Over It interativo, Sky Uppy, Parkour | Precisa de apresentador jogando |
| Espectadores viram jogadores | Bumper Brawl, Crowd Race, Agar.io | Comentar = entrar com o próprio nome |
| Suspense ("vai estourar?") | Melon Burst, Melancia de Elástico, DEFUSE! | Simples e viciante |
| Terror / zumbis com nomes | Dead Run, Silent Ward | Presente = monstro com o nome de quem enviou |
| Esportes | Pênalti, Copa Brasil, Basquete | Cada presente vira um chute ou lance |
| Minecraft / Roblox com presentes | TNT Fill, Building Defense, Protect Wheat, Red vs Blue | Precisa de alguém jogando |
| Palavras e desenho | Adivinhe o Desenho, STOP, Qual é a Palavra | Ótimo para a participação gratuita |

### Recurso oficial novo: "Viewer Wishes"
Desde agosto de 2025, alguns criadores podem configurar **até 6 ações** que fazem quando recebem um presente específico (Social Media Today). Combina com os blocos em que há apresentador.

**Atenção:** em 2026 houve notícias de que o TikTok **apertou o cerco contra lives "NPC" com ações repetitivas** (manchete da Folha Vitória; não consegui ler o texto completo). Isso reforça a regra oficial contra "ações monótonas e repetitivas". Por isso todo jogo deste catálogo tem **objetivo, rodadas, variação e narração**.

---

## Parte 2: padrões universais (valem para todos os jogos)

### Escala padrão de presentes (use em todas as fichas)
| Faixa | Moedas (custo do presente) | Uso no jogo | Exemplos de presente |
|---|---|---|---|
| **P** (pequeno) | 1–9 | Efeito pequeno e rápido, com o nome na tela | Rose, TikTok, Finger Heart |
| **M** (médio) | 10–99 | Poder especial visível | Doughnut (≈30) |
| **G** (grande) | 100–999 | Evento que muda a rodada | Hand Hearts (≈100) e similares |
| **L** (lendário) | 1.000+ | Animação de **tela cheia**, nome gigante, "momento" da live | Galaxy (1.000), Lion, Universe |

A BeetGames usa uma escala parecida: 1 moeda = empurrão pequeno; 20 = perturbação média; 100 = monstro ou explosão; 500 = sabotagem enorme ou efeito de vitória.

### Os 8 elementos obrigatórios de todo jogo
1. **Entrada grátis:** comentar ou curtir já participa.
2. **Nome na tela** em toda ação relevante.
3. **Efeito proporcional:** P pequeno, L espetacular.
4. **Rodadas de 2 a 6 minutos**, com intervalo de 10–20 s mostrando os destaques.
5. **Modo atração:** algo se mexe mesmo sem eventos, para nunca ter tela parada.
6. **Anti-spam:** espera por pessoa nos comentários e curtidas agrupadas.
7. **Marco comemorável:** vitória, recorde, chefe derrotado, "estourou".
8. **Placar que fica:** vitórias do dia e liga semanal.

### Checklist de regras (passe cada ideia por aqui)
- [ ] Nenhum prêmio real (dinheiro, Pix, produto, moedas)
- [ ] Sem sorteio ou roleta que dê algo de valor a quem participa
- [ ] Não é só "A ou B, mande presente": existe jogo de verdade
- [ ] Sem política, tragédia ou tema polarizador
- [ ] Ninguém "sofre" ou "morre" por falta de presente (pressão emocional)
- [ ] Os textos não pedem presente repetidamente
- [ ] Arte, música e sons próprios ou licenciados

### Como uma ficha vira jogo (o pipeline no nosso motor)
```
ficha  →  src/games/<id>.js        (regras: handle(evento), tick(tempo), snapshot())
       →  public/<id>/index.html   (o que aparece na live)
       →  config.json › games.<id> (números ajustáveis sem mexer no código)
       →  src/games/index.js       (registrar)
       →  testar:  node src/server.js --simulate --game=<id>
```
Cada ficha abaixo já diz **o que vai em cada parte**.

### Legenda das fichas
- **Sozinho:** ✅ roda sem ninguém jogando (o apresentador só narra) · 🎙️ precisa de apresentador jogando ou reagindo.
- **Esforço:** **P** = 1–2 dias · **M** = 3–5 dias · **G** = 1–2 semanas (estimativa para o nosso motor).
- **Potencial:** ★ a ★★★★★ (minha avaliação com base nos padrões de mercado e não em dados de receita, que só teremos medindo).
- **Risco de regras:** 🟢 baixo · 🟡 médio (exige cuidado no texto ou no desenho) · 🔴 evitar.

---

## Parte 3: as fichas

### ESTILO A — Times e corridas

#### A1. Corrida de Elementos ✅ · P · ★★★★ · 🟢 — **PRONTO** (`corrida`)
- **Conceito:** 4 times correm até a linha de chegada.
- **Entrar:** comentar 1, 2, 3 ou 4 (quem não escolhe vai para o time menor).
- **Eventos:** comentário +0,5 · 10 curtidas +1 · seguir +5 · compartilhar +8 · presente = 4 pontos por moeda. Rose = turbo · Finger Heart = escudo · Doughnut = congela o líder · Galaxy = MEGA turbo.
- **Rodada:** até 4 min ou quando alguém cruza a linha; 20 s de pódio.
- **Config:** `games.corrida` (times, pontos, efeitos).

#### A2. Corrida de Países ✅ · P · ★★★★★ · 🟢
- **Conceito:** 6–8 países (bandeiras ou animais) correm. Fica forte com público internacional e em época de Copa.
- **Entrar:** comentar o nome ou a sigla do país (BR, AR, PT, US…).
- **Eventos:** igual à A1. **Diferença:** cada país tem o **seu presente**, e esse presente vale dobrado para ele (mecânica do Animal Run).
- **Tela:** pistas horizontais com as bandeiras, ranking de países no dia.
- **Implementar:** copiar `corrida.js`, mudar `teams` para países e adicionar `teamGift` por time.

#### A3. Cabo de Guerra ✅ · P · ★★★★★ · 🟢
- **Conceito:** 2 times puxam uma corda; a marca central precisa chegar à linha do adversário.
- **Entrar:** comentar "A" ou "B" (ou o nome dos times).
- **Eventos:** curtidas puxam pouco · comentário puxa · P = puxão · M = puxão forte com tremor na tela · G = "âncora" (o time não escorrega por 10 s) · L = "SUPER PUXÃO" que move 30% da corda.
- **Rodada:** até 3 min; se ninguém cruzar a linha, vence quem estiver mais perto.
- **Cuidado 🟡:** os times devem ser **de jogo** (cores, elementos, bichos) e nunca "opiniões" (política, "quem é melhor").
- **Implementar:** estado = `pos` de −100 a +100; `add(team, pts)` move para o lado do time.

#### A4. Torre vs Torre ✅ · M · ★★★★ · 🟢
- **Conceito:** cada time empilha blocos; vence a torre mais alta ou a primeira a chegar à meta.
- **Eventos:** comentário = 1 bloco com o nome · curtidas = blocos pequenos · P = bloco grande · M = "andaime" (3 blocos) · G = **derruba 5 blocos** da torre rival · L = "guindaste" (+20 blocos).
- **Física opcional:** blocos tortos balançam (dá para usar a biblioteca Matter.js).

#### A5. Grand Prix do Chat ✅ · M · ★★★★ · 🟢
- **Conceito:** cada pessoa que comenta ganha **o próprio carro** com o nome (até 30 na pista).
- **Eventos:** P = DRS · M = nitro · G = mancha de óleo atrás de si · L = "carro foguete" por 5 s. Curtidas dão um pequeno impulso a todos.
- **Rodada:** 3 voltas; pódio com o top 3.
- **Diferencial:** **todo comentador vira jogador**, o que dá identificação imediata.

#### A6. Batalha dos Estados ✅ · G · ★★★★★ · 🟡
- **Conceito:** mapa do Brasil em pixels; cada estado conquista território a partir das próprias fronteiras.
- **Entrar:** comentar a sigla (SP, BA, MG…).
- **Eventos:** pontos pintam pixels na fronteira · G = "bomba de tinta" · L = "invasão" (pinta uma área grande com o nome do doador).
- **Rodada:** 10 min; ranking do dia e campeão da semana.
- **Cuidado 🟡:** moderar xingamentos regionais e não usar símbolos políticos.

#### A7. Guerra de Castelos ✅ · M · ★★★★ · 🟢
- **Conceito:** 2 castelos trocam mísseis; vence quem derrubar a muralha do outro (mecânica do Missile War).
- **Eventos:** comentário = flecha · P = bala de canhão · M = míssil · G = conserta a própria muralha · L = "chuva de meteoros" no inimigo.

#### A8. Empurra-Empurra ✅ · P · ★★★ · 🟢
- **Conceito:** uma bola gigante no meio; cada time empurra para o gol do outro.
- **Eventos:** tudo empurra; L = "chute de bicicleta" que atravessa meio campo.

#### A9. Círculos que Crescem ✅ · P · ★★★ · 🟢
- **Conceito:** cada time é um círculo; os eventos fazem crescer; quando dois se tocam, o maior "come" um pedaço do menor.
- **Rodada:** 3 min; vence o maior.

#### A10. Ponte dos Capitães ✅ · M · ★★★ · 🟢
- **Conceito:** os dois primeiros que comentarem "capitão" viram capitães; os times constroem pontes até o outro lado (mecânica do Bridge Rush).
- **Eventos:** cada contribuição adiciona tábuas; G = remove 3 tábuas do rival.

---

### ESTILO B — Cooperativos (todos contra o jogo)
Funcionam com **pouco público** e não dividem a live em lados. São ideais para começar.

#### B1. Raid do Chefão ✅ · M · ★★★★★ · 🟢 — **PRONTO** (`chefao`)
- **Conceito:** todo o chat ataca um chefe; quando ele cai, vem outro mais forte (vida ×1,35 por nível).
- **Eventos:** comentário = golpe de 2 (espera de 3 s) · 10 curtidas = 1 · seguir 25 · compartilhar 40 (crítico) · presente = **10 de dano por moeda**. Faixas: Ataque (P), Raio (M ≥ 30), Meteoro (G ≥ 100), ULTIMATE (L ≥ 1.000).
- **Fim do chefe:** top 3 de dano + "último golpe por @nome"; 10 s até o próximo.
- **Modo atração:** "aliados" tiram 1,5 de vida por segundo, então a tela sempre se mexe.
- **Config:** `games.chefao` (vida, crescimento, dano, faixas, lista de chefes).

#### B2. Defesa da Base ✅ · G · ★★★★★ · 🟢
- **Conceito:** ondas de monstros andam até a base; o chat coloca defesas (tower defense).
- **Eventos:** comentário = soldado com o nome · P = torre de flechas · M = canhão · G = muralha de gelo · L = "dragão aliado" que limpa a tela.
- **Rodada:** sobreviver a 10 ondas = vitória; a base cai = recomeça na onda 1, com o recorde salvo.
- **Por que é forte:** tensão constante e muitos "marcos" (onda 5, onda 10, recorde).

#### B3. Encher o Reservatório ✅ · P · ★★★ · 🟢
- **Conceito:** um tanque precisa encher até a marca; vazamentos aparecem sozinhos.
- **Eventos:** comentário = copo · curtidas = gotas · P = balde · M = mangueira · G = conserta todos os vazamentos · L = caminhão-pipa.
- **Variante com 2 lados:** "heróis enchem e vilões esvaziam" (existe no mercado). 🟡 Mantenha como jogo e não como pressão.

#### B4. Desarme a Bomba ✅ · M · ★★★★ · 🟢
- **Conceito:** uma bomba de desenho animado com cronômetro e 4 fios. O chat precisa **adivinhar a sequência certa** comentando as cores.
- **Eventos:** comentário = palpite · curtidas = +1 s no relógio · P = revela se a próxima cor é quente ou fria · M = congela o relógio 5 s · G = revela um fio certo.
- **Explodiu?** Animação engraçada e nova bomba. **Desarmou?** Nome de quem acertou em destaque.

#### B5. Monumento Coletivo ✅ · M · ★★★★ · 🟢
- **Conceito:** o chat constrói um monumento gigante (castelo, foguete, estátua) bloco a bloco, ao longo do **dia inteiro**.
- **Eventos:** cada bloco leva o nome de quem colocou; G e L constroem partes "especiais" (torre, bandeira).
- **Marco:** ao completar, uma animação de lançamento (o foguete decola) e o próximo começa.
- **Bom para:** o fim da noite e a lembrança de "eu ajudei a construir".

---

### ESTILO C — Suspense e física

#### C1. Estoura ou Não ✅ · P · ★★★★ · 🟢 — **PRONTO** (`melancia`)
- **Conceito:** elásticos numa melancia (ou abóbora, balão, coco); o limite é **escondido** (entre 60 e 180).
- **Eventos:** comentário 1 (espera de 5 s) · 30 curtidas = 1 · seguir 3 · compartilhar 5 · presente = 1 elástico por moeda (no máximo 40 por evento).
- **Marco:** "EXPLODIU por @nome" + "Reis do estouro" do dia.
- **Nota de regras:** é aleatório, mas **não vale prêmio**, só reconhecimento. Mantenha assim.

#### C2. Torre de Gelatina ✅ · M · ★★★★ · 🟢
- **Conceito:** blocos de gelatina com física caem e se empilham; blocos iguais se fundem (o mecânico "stack & merge" do Jello Merge). Se a torre passar da linha, ela cai.
- **Eventos:** P = bloco pequeno · M = bloco grande · G = "cola" que segura a torre 10 s · L = bloco dourado que vale 5.

#### C3. Gangorra do Equilíbrio ✅ · M · ★★★ · 🟢
- **Conceito:** objetos com nomes caem numa gangorra; se ela tombar demais, tudo cai e a rodada acaba. O recorde é o número de objetos de pé.

#### C4. Cobra Gigante ✅ · P · ★★★ · 🟢
- **Conceito:** uma cobra anda sozinha; cada moeda vira uma maçã na tela, e 1.000 moedas viram uma "chuva de maçãs".
- **Variante duelo:** 2 cobras, uma por time (o Snake Duel do mercado).

#### C5. Agar do Chat ✅ · G · ★★★★ · 🟢
- **Conceito:** cada participante é uma célula com o próprio nome; as células comem bolinhas e as menores. Presentes fazem a célula crescer.
- **Cuidado:** é preciso limitar o número de jogadores na tela (máximo de 50).

#### C6. Arena de Batidas ✅ · M · ★★★★ · 🟢
- **Conceito:** os participantes viram carrinhos de bate-bate numa arena que encolhe; o último na arena vence (o battle royale Bumper Brawl).
- **Eventos:** comentário = entra · P = impulso · M = escudo · G = onda de choque · L = "tamanho gigante" por 10 s.

#### C7. Exército de Nomes ✅ · G · ★★★★★ · 🟢
- **Conceito:** cada participante vira um soldado; presentes criam unidades maiores (cavaleiro → gigante → dragão). Os exércitos lutam sozinhos (o "spawn wars", do StarCraft 2 interativo).
- **Por que é forte:** ver o **próprio dragão com o próprio nome** é a recompensa de reconhecimento perfeita.

#### C8. Queda de Bolinhas por Time ✅ · M · ★★★ · 🟡
- **Conceito:** bolinhas com nomes caem por pinos e somam pontos para o **time** de quem enviou.
- **Cuidado 🟡:** parece "plinko". **Nunca** pode valer prêmio. Prefira os outros jogos deste estilo, porque a imagem de "plinko" pode ser associada a jogo de azar.

---

### ESTILO D — Palavras e conhecimento (participação grátis forte)
Geram muitos **comentários**, o que aumenta o engajamento e a distribuição. Os presentes dão vantagem, mas nunca são obrigatórios.

#### D1. Quiz Relâmpago ✅ · M · ★★★ · 🟢
- **Conceito:** pergunta com 4 alternativas na tela; quem acerta primeiro (comentando A, B, C ou D) leva mais pontos (100, 90, 80…).
- **Eventos:** P = elimina uma alternativa errada (para todos) · M = "pontos em dobro" para o doador · G = "pergunta bônus" · L = "rodada relâmpago" com 5 perguntas seguidas.
- **Conteúdo:** banco de perguntas em JSON (podemos gerar milhares por tema: futebol, novela, música, geografia).

#### D2. Palavra Secreta ✅ · P · ★★★ · 🟢
- **Conceito:** uma palavra escondida (com a categoria como dica); o chat chuta letras ou a palavra inteira.
- **Eventos:** curtidas: a cada 200, uma letra é revelada · P/M = revela uma letra · L = revela a palavra (o doador fica com o crédito "deu a dica de ouro").

#### D3. STOP / Adedonha ✅ · M · ★★★★ · 🟢 — **muito brasileiro**
- **Conceito:** a letra é sorteada e a categoria aparece (nome, fruta, cidade); respostas **únicas** valem 100 e repetidas dividem os pontos.
- **Eventos:** presentes dão "+10 s" ou "x2" para o doador. É muito conhecido no Brasil.

#### D4. Adivinhe pelo Emoji ✅ · P · ★★★ · 🟢
- **Conceito:** filme, música ou novela em emojis 🦁👑 → "Rei Leão". Primeiro a acertar ganha.

#### D5. Contagem Coletiva ✅ · P · ★★★ · 🟢
- **Conceito:** o chat conta 1, 2, 3… em ordem, mas a mesma pessoa não pode comentar dois números seguidos. Errou? Volta a zero. O recorde do dia fica na tela.
- **Eventos:** P = "vida extra" (um erro perdoado) · L = "pula 50".

---

### ESTILO E — Esportes

#### E1. Pênalti Automático ✅ · M · ★★★★ · 🟢
- **Conceito:** cada presente é um **chute com o nome na camisa**; um goleiro de IA defende (o mecânico do Penalty). O placar compara gols e defesas.
- **Eventos:** P = chute normal · M = chute colocado (mais chance) · G = "cavadinha" animada · L = "chuva de gols" (10 chutes).
- **Comentários:** escolhem o canto (E, M ou D), uma participação gratuita que também conta pontos.

#### E2. Copa de Times ✅ · M · ★★★★★ · 🟢
- **Conceito:** partida de futebol de botão entre 2 times (de jogo, sem marcas oficiais); os eventos empurram a bola.
- **Cuidado:** **não use escudos ou nomes oficiais de clubes** (marca registrada). Use apelidos e cores.

#### E3. Arremesso de Basquete ✅ · P · ★★★ · 🟢
- **Conceito:** cada interação é um arremesso com o nome; uma sequência de cestas dá "ON FIRE".

---

### ESTILO F — Com apresentador (os que mais rendem quando há alguém)
Use nos **blocos de presença** do modelo de operação (arquivo 02).

#### F1. Ajuda ou Atrapalha 🎙️ · G · ★★★★★ · 🟢
- **Conceito:** o apresentador joga uma escalada ou plataforma difícil (**feita por nós**, para não ter problema de licença).
- **Eventos:** P "ajuda" = pulo extra · P "atrapalha" = vento · M = inimigo com o nome · G = derruba um andar · L = "teletransporte para o topo" (ou para o chão).
- **Implementar:** o jogo roda no navegador e o apresentador controla pelo teclado; os eventos chegam pelo mesmo servidor.

#### F2. Fuga do Monstro (terror leve) 🎙️ · G · ★★★★ · 🟢
- **Conceito:** o apresentador foge num labirinto; presentes criam **zumbis com o nome** de quem enviou; presentes escolhidos dão armas ao apresentador (o Dead Run).
- **Cuidado:** terror leve e cartunesco. Nada de violência gráfica.

#### F3. Minecraft com Presentes 🎙️ · P (sem programar) · ★★★★★ · 🟢
- **Como:** TikFinity → Actions & Events → conector de Minecraft (TikfiniTok ou ServerTap).
- **Exemplo de mapa de presentes:** Rose = TNT pequena · Finger Heart = 3 zumbis · seguir = galinha · M = creeper · G = Wither (chefe) · L = "chuva de TNT".
- **Mapas populares:** TNT Fill, Building Defense, Protect Wheat, Red vs Blue.

#### F4. Desafios do Apresentador 🎙️ · P · ★★★ · 🟡
- **Conceito:** metas de presentes destravam ações divertidas do apresentador (cantar, imitar, sotaque). Use o recurso oficial **Viewer Wishes** (até 6 ações).
- **Cuidado 🟡:** varie as ações e **converse**. A mesma ação repetida mecanicamente é o formato "NPC" que o TikTok está restringindo.

#### F5. Votação de Rumo 🎙️ · P · ★★★ · 🟢
- **Conceito:** o chat vota o que o apresentador faz no jogo ("ir pela esquerda ou pela direita?"). Use **Poll & Gift vote**, recurso oficial do LIVE Studio.

---

### ESTILO G — Mundos persistentes (fazem o público voltar)

#### G1. Fazenda da Live ✅ · G · ★★★★★ · 🟢 — ⭐ **ver a ficha detalhada abaixo**
#### G2. Aquário Coletivo ✅ · M · ★★★★ · 🟢
- **Conceito:** cada presente coloca um peixe **com o seu nome**; ele cresce com as curtidas e **continua lá amanhã**. Presentes L trazem espécies raras (tubarão, baleia).
- **Proibido:** "o peixe morre se não mandar presente" (pressão emocional).

#### G3. Cidade dos Seguidores ✅ · G · ★★★★ · 🟢
- **Conceito:** cada novo seguidor ganha uma casinha com o nome numa cidade que cresce; presentes constroem prédios, praças e um estádio. A cidade vira o "patrimônio" do canal e um ótimo assunto para os vídeos de cortes.

---

### ESTILO H — Camadas extras (combinam com qualquer jogo)
| # | Camada | Como funciona | Status |
|---|---|---|---|
| H1 | **Rodízio de jogos** | Troca de jogo a cada X minutos, sempre entre rodadas | **PRONTO** (`config.json › rotation`) |
| H2 | **Liga Semanal** | Pontos de todos os jogos somam num ranking semanal; o campeão fica com um destaque fixo | Próximo passo |
| H3 | **Maratona com relógio** | "Extendable countdown" oficial do LIVE Studio | Configurar no LIVE Studio |
| H4 | **Metas oficiais** | LIVE goal, Gift Gallery e Fan Club por cima do jogo | Configurar no LIVE Studio |
| H5 | **Cortes automáticos** | O Replay Buffer do OBS salva os "momentos L" para virarem vídeos | Configurar no OBS |
| H6 | **Narrador por voz (TTS)** | Lê o nome e a ação ("Ana lançou um METEORO!") | Pode ser feito com o TikFinity |

---

## Parte 4: ficha detalhada da "Fazenda da Live" (G1)
> Vi que o seu computador tem uma pasta chamada **"jogo de fazenda"**. Se a sua ideia for uma fazenda, este é um ponto de partida já no formato do nosso motor. Ajuste à vontade, e se a sua ideia for outra, me mande que eu adapto.

**Conceito:** uma fazenda coletiva que cresce ao longo do dia e **continua amanhã**. Cada espectador pode ter o **próprio canteiro ou animal com o nome**. As rodadas são as **"colheitas"** de 5 min.

| Ação | Efeito |
|---|---|
| Comentar "plantar" (ou só comentar) | Ganha um canteiro com o nome e uma semente plantada |
| Comentar "regar" | Rega o próprio canteiro (espera de 30 s) |
| Curtidas | "Chuva": todas as plantas crescem um pouco |
| Seguir | Ganha um animal (galinha) com o nome |
| Compartilhar | "Adubo" para a fazenda inteira |
| **P** (1–9 moedas) | Fertilizante: a planta do doador cresce na hora |
| **M** (10–99) | Animal novo (vaca, porco) com o nome |
| **G** (100–999) | Trator: colhe e replanta uma fileira inteira |
| **L** (1.000+) | "Festa da Colheita": fogos, todas as plantas ficam douradas e o nome do doador aparece gigante |

- **Rodada (colheita, 5 min):** no fim, a fazenda colhe e mostra o "**Top fazendeiros**" (quem mais produziu) e a "**produção total do dia**".
- **Progressão:** a fazenda sobe de nível a cada X colheitas e desbloqueia celeiro, moinho, lago e pomar. **Isso fica salvo** (arquivo JSON em `data/`).
- **Eventos aleatórios (variedade, sem pressão):** corvo aparece (as curtidas espantam), arco-íris (bônus de crescimento), estação do ano muda a cada hora.
- **Modo atração:** as plantas crescem devagar sozinhas e os animais andam pela tela.
- **Tela vertical:** topo com o nome da fazenda, nível e estação · meio com a grade de canteiros com nomes · fundo com "Top fazendeiros" e o feed.
- **Regras:** 🟢. Nada morre por falta de presente: a planta não regada só para de crescer.
- **Esforço:** G (1–2 semanas), com a arte como maior trabalho. A versão 1 pode usar emojis (🌱🌽🐔🐄🚜), como fiz nos jogos prontos.
- **Config sugerida:** `games.fazenda = { plots: 48, harvestSeconds: 300, growthPerLikeBatch, growthPerCoin, animals: [...], levels: [...] }`

---

## Parte 5: ordem recomendada de desenvolvimento
| Ordem | Jogo | Por quê |
|---|---|---|
| ✅ | B1 Raid do Chefão, A1 Corrida, C1 Estoura ou Não | **Prontos.** Já dá para rodar em rodízio |
| 1 | **G1 Fazenda da Live** (ou o seu jogo) | Mundo persistente que faz o público voltar, e combina com a sua ideia |
| 2 | **A3 Cabo de Guerra** | O formato mais citado no mercado; esforço P |
| 3 | **C7 Exército de Nomes** | O maior apelo de reconhecimento (o próprio dragão) |
| 4 | **D3 STOP/Adedonha** | Gera muitos comentários, é brasileiro e diferencia o canal |
| 5 | **H2 Liga Semanal** | Faz voltar: vale para todos os jogos |
| 6 | **E1 Pênalti** + **A2 Países** | Para datas de futebol |

**Como decidir com dados:** o `api/stats` agora separa as moedas **por jogo** (`byGame`) e **por hora** (`byHour`). Depois de 1–2 semanas de rodízio, ficamos com os jogos que mais rendem e trocamos os que menos rendem.

---

## Fontes desta pesquisa
- Ipsos / TikTok, estudo de presentes: [Tubefilter, 02/12/2025](https://www.tubefilter.com/2025/12/02/tiktok-live-ipsos-virtual-gifting-data-study/amp/) · [PDF Ipsos](https://www.ipsos.com/sites/default/files/documents/2025-12/Ipsos-Entertainment%20Redefined%20-%20Interactive%20Livestream%20Gifting.pdf)
- Viewer Wishes: [Social Media Today, 19/08/2025](https://www.socialmediatoday.com/news/tiktok-adds-live-performance-requests-for-donations/758100/)
- Restrição a lives NPC (só a manchete; texto não lido): [Folha Vitória](https://www.folhavitoria.com.br/cultura/tiktok-aperta-o-cerco-contra-lives-de-npc-por-acoes-repetitivas/)
- Catálogos e formatos (sites de fornecedores; números são propaganda): [ScrollPlay — 14 formatos](https://scrollplay.live/guides/tiktok-live-game-ideas) · [TikLive Games — catálogo](https://tiklivegames.io/games) · [BeetGames](https://beetgames.com/) · [TikXander](https://www.tikxander.com/en/guides/best-interactive-games-for-tiktok-live) · [Interactive Live](https://interactivelivegames.com/en) · [TiktokBoxGame](https://tiktokgames.live/best-interactive-games-tiktok-live-2026.html) · [itch.io — tiktok-interactive](https://itch.io/games/tag-tiktok-interactive) · [HgGamesStudio — ideias](https://hggamesstudio.bsite.net/en/blog/details/creative-interactive-game-ideas-for-tiktok-live-streamers)
- Regras oficiais: ver o arquivo `01-regras-oficiais-e-riscos.md` e o `08-fontes.md`
