# 03 — Dinheiro: economia dos presentes, saque, cenários e custos

> O TikTok **não publica uma taxa oficial fixa**. A Política de Itens Virtuais deixa a conversão a critério dele. Os números abaixo são o **consenso de várias fontes de terceiros** em 2026. Use-os para planejar e depois troque pelos dados reais que o protótipo registra.

## 3.1 Como o dinheiro anda
```
Espectador compra MOEDAS  →  envia PRESENTE (custa X moedas)  →  você recebe DIAMANTES  →  saca em dinheiro
   ~US$ 0,0106/moeda                                              ~50% do valor             ~US$ 0,005 por moeda enviada
```
- **Preço da moeda para quem compra:** cerca de US$ 0,0106 (exemplo: pacote de 660 moedas por US$ 6,99, preço dos EUA). Varia por país e por canal de compra: na web costuma sair mais barato que no app.
- **Parte do criador:** cerca de **50%**. Algumas análises calculam um corte efetivo do TikTok de 50% a 60% somando todas as etapas.
- **Regra prática:** **cada 1.000 moedas em presentes ≈ US$ 5 para você** (1 diamante ≈ US$ 0,005).
- **No código:** o campo `giftDetails.diamondCount` da biblioteca é o **custo do presente em moedas** (Rose = 1). Por isso o protótipo estima `moedas × 0,005 = US$`.

## 3.2 Tabela de presentes (custo em moedas → quanto você recebe)
| Presente | Moedas | ≈ Você recebe | Confiança |
|---|---|---|---|
| Rose | 1 | US$ 0,005 | Alta (consenso) |
| TikTok | 1 | US$ 0,005 | Média |
| Finger Heart | 5 | US$ 0,025 | Média (fonte de terceiros) |
| Doughnut | 30 | US$ 0,15 | Confira no app |
| Galaxy | 1.000 | ≈ US$ 5 | Alta |
| Lion | 29.999 | ≈ US$ 150 | Alta (moedas); o valor em dólar diverge entre fontes |
| Universe | 34.999 ou 44.999 (varia por região) | ≈ US$ 175–225 | Média |

O catálogo de presentes **muda sem aviso** e varia por região. O protótipo lê o nome e o custo de cada presente **ao vivo**, então não depende desta tabela.

## 3.3 Saque no Brasil
- O saque é feito no app, na área de recompensas/LIVE (Saldo → Sacar).
- **Métodos citados por fontes brasileiras:** PayPal (mínimo de **US$ 10**; é preciso **aceitar** a transferência no PayPal, senão o valor volta), Pix (sem mínimo explícito, mas há relatos de erro abaixo de R$ 20) e conta bancária. Podem existir taxas de cerca de 2–4%.
- As fontes divergem entre si. **Confira o mínimo e os métodos no próprio app** antes do primeiro saque.
- **18 anos ou mais** para receber presentes e sacar diamantes (Diretrizes da Comunidade e Política de Itens Virtuais).

## 3.4 O que dizem os dados de mercado (todos de terceiros)
- **Mediana:** a maioria dos streamers ativos recebe **menos de US$ 5 por hora** em presentes (StreamWrapped).
- **Faixa citada para lives de jogos:** US$ 25–120 por hora, mas isso vale para quem já tem público.
- **Exemplo de calculadora:** 4 pessoas dando presentes por hora renderam US$ 7,43 líquidos por hora (RevenueLab).
- **Por público:** 100–500 espectadores simultâneos dão US$ 30–150 por live; 500–1.000 dão US$ 150–500.
- **Brasil:** reportagem citando a Business of Apps diz que **só 1% dos criadores ativos de LIVE recebe mais de US$ 50 por mês**. É uma fonte secundária, mas mostra que a concorrência é grande e que **o formato e a consistência fazem a diferença**.
- **Dado oficial do TikTok:** "Around 70% of LIVE creators achieve their goals" (metas de presentes) e "85% of LIVE creators have successfully gained more followers and rewards by leveraging these tools" (ferramentas de interação).

**Leitura honesta:** a receita depende de **poucos doadores grandes** e de **público recorrente**. O jogo é a ferramenta para transformar espectador em participante e participante em doador. Não existe garantia de lucro: o plano é **medir, ajustar e escalar** só o que der resultado.

## 3.5 Cenários (fórmula + simulação)
**Fórmula:** `Receita/hora (US$) = doadores/hora × moedas médias por doador × 0,005`

| Cenário | Doadores/h | Moedas/doador | Moedas/h | **US$/h** | Nível A (5 h/dia, 30 dias) | Nível B (16 h/dia, 30 dias)* |
|---|---|---|---|---|---|---|
| Pessimista (início) | 2 | 20 | 40 | 0,20 | US$ 30/mês | US$ 96/mês |
| Base | 6 | 60 | 360 | 1,80 | US$ 270/mês | US$ 864/mês |
| Bom | 15 | 120 | 1.800 | 9,00 | US$ 1.350/mês | US$ 4.320/mês |
| Excelente | 40 | 200 | 8.000 | 40,00 | US$ 6.000/mês | US$ 19.200/mês |

\* No nível B as horas de madrugada e manhã rendem menos que o horário nobre, então a receita **não cresce em linha reta** com as horas. Use o registro diário para ver quais horas pagam.

Para converter em reais, multiplique pelo câmbio do dia. Com um câmbio **ilustrativo** de R$ 5,50, o cenário Base no nível A dá cerca de R$ 1.485 por mês.

## 3.6 Custos mensais (estimativas)
| Item | Custo | Observação |
|---|---|---|
| Energia do PC | ≈ R$ 85–130 | 200 W médio, 16–24 h por dia, tarifa ilustrativa de R$ 0,90/kWh. Confira a tarifa da sua conta. |
| Internet | Você provavelmente já tem | Fibra com **upload ≥ 10–20 Mbps** estável |
| Sinal da conexão (Euler Stream) | **US$ 0** (plano gratuito) | O plano gratuito dá 1.500 requisições/dia e 25 WebSockets, suficiente para 1 live. O pago começa em US$ 50/mês. |
| TikFinity (opcional) | Grátis / Pro ≈ US$ 19/mês | O preço do Pro vem de fonte de terceiros |
| Jogos prontos (opcional) | US$ 6–14/mês | Ver arquivo 06 |
| Apresentadores (nível B) | Você define | Ver o ponto de equilíbrio abaixo |
| Nobreak (uma vez) | Compra única | Evita que a live caia com queda de luz |

### Ponto de equilíbrio de um apresentador
`Contrate a hora só se: receita/hora daquele horário > custo/hora do apresentador`
Exemplo: com apresentador a R$ 15/h e câmbio de R$ 5,50, a hora precisa render mais de US$ 2,73. Isso dá **≈ 546 moedas por hora em presentes** (`2,73 ÷ 0,005`).
**Estratégia:** comece só com você no nível A, meça por 2–4 semanas e contrate apresentadores **apenas para os horários que já passam desse valor**. Outra opção é pagar o apresentador por **porcentagem da receita do turno**, o que elimina o risco de prejuízo.

## 3.7 Outras fontes de receita que combinam com o projeto
- **Ferramentas oficiais de interação** (LIVE Studio): LIVE goal, Gift Gallery, Extendable countdown (a live estende quando chegam presentes, para maratonas), Fan Club e Assinatura de LIVE. Use **todas**, porque elas ficam por cima do jogo.
- **Cortes em vídeo:** o OBS grava as melhores jogadas e você posta 2–3 vídeos por dia. Isso traz seguidores para a live. Se a conta for elegível ao programa de recompensas por vídeo, os vídeos também rendem; confira a disponibilidade e os requisitos no app.
- **Venda do próprio jogo para outros streamers:** se o seu jogo fizer sucesso, dá para vendê-lo ou alugá-lo, como fazem os vendedores do arquivo 06.
