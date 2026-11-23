# 02 — Modelo de operação "o dia inteiro"

## 2.1 O objetivo, reescrito em cima das regras
**Objetivo:** a live roda o máximo de horas por dia, dá dinheiro e você não precisa ficar mexendo em nada técnico.
**Limite:** o TikTok desmonetiza live "operada principalmente por sistemas automáticos sem presença significativa do criador" (arquivo 01).

**Solução:** separar o trabalho em duas camadas.

| Camada | Quem faz | O que inclui |
|---|---|---|
| **Técnica (≈95% do trabalho)** | **O PC, sozinho** | Ligar e desligar a live nos horários; rodar o jogo; contar pontos; placar; alertas; ler o nome de quem deu presente (TTS); trocar de rodada; reconectar se a internet cair; reiniciar se travar; salvar a receita; avisar se algo der errado |
| **Presença (≈5% do esforço, mas obrigatória)** | **Você ou um apresentador** | Rosto e voz na câmera, reagir ao jogo, falar o nome das pessoas, comentar as jogadas |

Para o apresentador, a tarefa é simples: **ser a cara da live**. Ele não precisa saber nada de técnica, porque o jogo se conduz sozinho.

## 2.2 Três níveis de operação (escolha um)

### Nível A — "Horário nobre" (recomendado para começar)
- **4 a 6 horas por dia**, nos horários de maior audiência que você medir. Comece testando o fim da tarde e a noite.
- Você aparece. O PC faz todo o resto.
- Fora desse horário a live fica **desligada**. Nesse tempo, o PC grava **cortes** das melhores jogadas para postar como vídeo, o que traz seguidores para a próxima live.
- **Custo extra:** zero. **Risco:** baixo.

### Nível B — "Dia inteiro com turnos" (escala)
- **12 a 18 horas por dia**, divididas em turnos de 3 a 4 horas.
- **Apresentadores contratados aparecem de casa**, sem precisar ir até o seu PC:
  - **Opção 1, VDO.Ninja** (gratuito): o apresentador abre um link no celular ou PC. A câmera e o microfone dele chegam ao seu OBS como uma fonte de navegador. Para quem assiste, ele está "na" sua live.
  - **Opção 2, Convidado (multi-guest/co-host) do TikTok:** o apresentador entra como convidado pelo app. É mais simples, mas o layout fica dividido. **A ser testado:** as regras não dizem se a presença de um convidado conta como "presença do criador".
- Você entra nos picos, se quiser.
- **Custo extra:** pagamento dos apresentadores (ver arquivo 03 para o ponto de equilíbrio). **Risco:** baixo, desde que haja sempre alguém presente.

### Nível C — "24h sem ninguém" (não recomendado)
- Tecnicamente o `starter/` roda assim. O controlador mantém o jogo e o OBS de pé 24h.
- **Mas** é exatamente o caso que as regras de monetização descrevem, e o risco é perder os presentes de forma permanente.
- Se mesmo assim quiser testar, faça **numa conta secundária, nunca na principal**, e acompanhe *LIVE Center › Restrictions*.

## 2.3 Rotina diária automatizada (nível A ou B)

```
07:50  Windows liga (BIOS "power on" programado ou PC sempre ligado)
07:55  O controlador (iniciado com o Windows) já está com o jogo e o OBS prontos → LIGAR no painel
08:00  Apresentador do turno 1 abre o link do VDO.Ninja → aparece na cena
        … o jogo roda sozinho: rodadas de 4 min, intervalos de 20 s …
12:00  Troca de turno (5 min de cena "intervalo" com o placar do dia)
        …
23:00  OBS para a transmissão (script/obs-websocket) → PC grava os cortes do dia
23:05  Relatório do dia (/api/stats) salvo no arquivo data/ledger-AAAA-MM-DD.jsonl
```

O detalhe técnico de cada passo está no arquivo 04.

## 2.4 O que o apresentador faz (roteiro de 1 página para entregar a ele)
1. Abrir o link da câmera 5 minutos antes do turno e conferir o som.
2. Cumprimentar quem entra **pelo nome**. Os nomes aparecem no feed do jogo.
3. Narrar a corrida ou batalha como um locutor: "o time Fogo está virando!".
4. Agradecer presentes de forma natural, sem pedir presente. Exemplo: "valeu, Ana, que turbo!"
5. **Nunca:** prometer prêmio, pedir presente por necessidade, fazer "follow for follow", falar de política, mostrar QR code ou link, ou tocar música com direitos autorais.
6. Precisou sair? Clicar em **Pausar LIVE**. Fim do turno sem substituto: o OBS encerra a live.

## 2.5 Por que esse modelo dá mais dinheiro, além de ser permitido
- Live com pessoa interagindo **retém mais**. Mais tempo assistindo leva a mais distribuição no feed LIVE e a mais presentes.
- Quem dá presente quer **ser reconhecido**: ouvir o próprio nome e ver o efeito no jogo. A reação humana multiplica isso.
- O jogo continua fazendo o trabalho pesado, então o apresentador não precisa ser "carismático profissional".
