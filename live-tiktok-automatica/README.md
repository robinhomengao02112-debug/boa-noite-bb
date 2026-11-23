# Projeto: Live Interativa Automática no TikTok

Pesquisa feita em **23/11/2026**. Tudo o que está aqui foi checado nas fontes listadas em [`08-fontes.md`](08-fontes.md).
Quando uma informação vem só de sites de terceiros (e não do TikTok), isso está escrito.

## Como usar esta pasta

| Arquivo | O que tem |
|---|---|
| [`01-regras-oficiais-e-riscos.md`](01-regras-oficiais-e-riscos.md) | As regras do próprio TikTok que decidem se a live ganha dinheiro ou não. **Leia primeiro.** |
| [`02-modelo-de-operacao-24h.md`](02-modelo-de-operacao-24h.md) | Como rodar o dia inteiro: o que a máquina faz sozinha e onde precisa de alguém. |
| [`03-dinheiro-e-economia-dos-presentes.md`](03-dinheiro-e-economia-dos-presentes.md) | Quanto vale cada presente, quanto o TikTok fica, saque no Brasil, contas de lucro e custos. |
| [`04-arquitetura-tecnica.md`](04-arquitetura-tecnica.md) | Como montar no seu PC: programas, conexão com a live, OBS, PC e internet, reinício automático. |
| [`05-ideias-de-jogos.md`](05-ideias-de-jogos.md) | 16 ideias de jogos com nota, mecânica e quais presentes fazem o quê. Mais a lista do que evitar. |
| [`06-ferramentas-e-custos.md`](06-ferramentas-e-custos.md) | Ferramentas prontas (TikFinity, jogos vendidos), APIs e preços. |
| [`07-plano-de-execucao-e-checklists.md`](07-plano-de-execucao-e-checklists.md) | Plano passo a passo, da conta até a operação, com checklists diários. |
| [`08-fontes.md`](08-fontes.md) | Todas as fontes, separadas entre oficiais e de terceiros. |
| [`09-ficha-do-seu-jogo.md`](09-ficha-do-seu-jogo.md) | Um modelo para você descrever o jogo que já tem em mente; eu implemento a partir dele. |
| [`starter/`](starter/) | **Um protótipo que já funciona:** jogo "Corrida de Elementos", conexão com a live, overlay para OBS, simulador, registro de receita e um supervisor que reinicia sozinho. |

---

## Resumo executivo (o essencial em 2 minutos)

### 1. O ponto mais importante: a live 100% sem ninguém perde o dinheiro
As **Diretrizes de Monetização de LIVE** oficiais do TikTok dizem que **podem perder a monetização** (ou seja, deixar de receber presentes):

> "Content that is generated or operated primarily through automated systems, such as AI or other third-party tools, **without meaningful creator presence, interaction, or facilitation** of the LIVE experience."
>
> "Content that is largely passive or **unattended**…"

A punição vai de aviso até **perda permanente** de Presentes de LIVE, LIVE Match e rankings.
**Conclusão:** uma live que roda sozinha 24h, sem ninguém, é exatamente o que essa regra descreve. Ela pode até ficar no ar, mas tende a **perder os presentes**, que são a fonte do dinheiro.
Detalhes e citações completas estão no arquivo 01.

### 2. O modelo que dá para fazer: a máquina faz 95%, uma pessoa aparece
O arquivo 02 traz o desenho completo. Em resumo:
- O **PC faz tudo sozinho**: roda o jogo, conta pontos, mostra placar e alertas, agradece presentes, reinicia se travar, salva a receita e liga/desliga nos horários certos.
- A **presença humana** fica em blocos: **você** nos horários de pico e, se quiser cobrir o dia todo, **apresentadores contratados em turnos**, que aparecem de casa pela webcam (via VDO.Ninja ou convidado da live).
- Quando não tem ninguém, a live fica **pausada ou desligada**. É o que o próprio TikTok recomenda.

### 3. Dinheiro: números reais
- O espectador paga cerca de **US$ 0,0106 por moeda** (preço nos EUA; varia por país).
- O criador recebe cerca de **50%**, ou seja, **≈ US$ 0,005 por moeda** de presente recebido.
- **1.000 moedas em presentes ≈ US$ 5 para você.**
- A **mediana** de quem faz live recebe **menos de US$ 5 por hora** em presentes, segundo dados de terceiros. Quem fatura alto tem público grande e alguns "baleias" (poucos espectadores que dão presentes grandes).
- Cenários de lucro e custos estão no arquivo 03. O protótipo já salva a receita estimada de cada dia (`/api/stats`), para trocarmos estimativa por dado real.

### 4. Requisitos para começar
- **18 anos ou mais** para fazer LIVE e para receber presentes (Diretrizes da Comunidade, versão em vigor desde 24/09/2026).
- **Cerca de 1.000 seguidores** para liberar a LIVE no Brasil. Esse número vem de guias de terceiros; o TikTok só diz que o requisito "varia por país".
- Para transmitir do PC: acesso ao **TikTok LIVE Studio** ou a uma **chave de transmissão** (RTMP) para o OBS.

### 5. Jogos recomendados (arquivo 05)
1. **Raid Cooperativo contra Chefão**: todo o chat contra um chefe. Funciona com pouca gente e não divide o público em lados.
2. **Corrida de Times**: já pronto no `starter/`.
3. **Batalha dos Estados**: mapa do Brasil em que cada estado conquista território. Usa o orgulho regional e é bem brasileiro.
4. **Para os blocos com apresentador**: "ajuda ou atrapalha" (o apresentador joga e o chat sabota), ou Minecraft com eventos de presentes via TikFinity.

### 6. O que evitar (pode tirar a monetização ou dar problema legal)
- Sorteio, roleta ou "plinko" valendo prêmio, e qualquer prêmio real em troca de presente. É jogo de azar: proibido pelo TikTok e contravenção no Brasil.
- "Time A contra time B, mande presente" sem jogo de verdade por trás. A regra cita "dois pontos de vista opostos".
- Temas políticos (eleição, políticos).
- "Pet que morre se ninguém der presente". É pressão emocional, citada na regra.
- QR code, link externo e música ou vídeo com direitos autorais.
- Vídeo gravado ou em loop fingindo ser ao vivo.

---

## Status
- [x] Pesquisa de regras, mercado, ferramentas, economia e jogos
- [x] Protótipo funcional (`starter/`), testado no simulador sem erros
- [ ] **Próximo passo:** você preenche o `09-ficha-do-seu-jogo.md` com a sua ideia, e eu transformo em jogo no mesmo motor do protótipo
