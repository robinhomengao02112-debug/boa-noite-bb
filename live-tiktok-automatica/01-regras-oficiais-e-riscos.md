# 01 — Regras oficiais do TikTok e riscos

> Estas regras decidem se a live **dá dinheiro ou não**. Todas as citações foram copiadas das páginas oficiais em 23/11/2026, com links em `08-fontes.md`.

## 1.1 Diretrizes de Monetização de LIVE (fonte oficial principal)
Página: *LIVE Creator Hub › LIVE Monetization Guidelines*.

### Princípio geral
> "LIVE content that involves monetization-related features is held to a higher integrity standard. Content or behavior that misleads users, manipulates engagement or rewards, or exploits platform mechanisms for unfair gain is ineligible for LIVE monetization."

> "These determinations are based on observable aspects of the LIVE experience, such as **authenticity, interaction, and creator participation**."

### "Inauthentic LIVE experience": o trecho que atinge a live automática
O TikTok **pode desmonetizar**:
- "Reproduced or unoriginal content … with no meaningful or creative edits" (conteúdo de outra pessoa, ou com marca d'água ou logo de terceiros).
- "**Pre-recorded videos, spliced videos, and looping videos** that are presented to viewers as if they are authentic LIVE experiences."
- "Content that is **generated or operated primarily through automated systems, such as AI or other third-party tools, without meaningful creator presence, interaction, or facilitation** of the LIVE experience."

### "Low quality content"
> "Content that is largely passive, repetitive, **unattended**, or lacking meaningful viewer engagement may be ineligible for monetization."
- "continuous and monotonous actions without clear objectives or direct interaction with viewers"
- "largely passive or unattended, such as sleeping during LIVE or performing routine or task-based activities without interaction"
- "prolonged blurred, obscured, or **static screens**"

### "Fake engagement and Gift baiting": cuidado no desenho do jogo
O TikTok **pode desmonetizar**:
- Conteúdo que "tricks, manipulates, or **mechanically and repeatedly solicits** viewers to increase Gifts".
- "misleading promises or artificial engagement exchanges … such as 'follow-for-follow' promises or **offering likes, follows, or Gifts in exchange for engagement**."
- Conteúdo que "exploit viewers' **emotions, sympathy, or sense of obligation** to pressure viewers into sending Gifts".
- "creators **solely present two or more opposing viewpoints** and ask viewers to send Gifts, comments, likes, or shares."
- Pedir presentes por necessidade financeira pessoal ou "vaquinha".

### Outros pontos
- **Menores de 18** como foco da live: desmonetiza.
- **Política e tragédias**, com conteúdo polarizador: desmonetiza. Contas de governo, político ou partido não monetizam.
- **Convidados:** o criador responde pelo que o convidado mostra.
- **Punições:** "in-app warning, temporarily restricting a LIVE's access to LIVE monetization features, or **permanently restricting an account's access** to these features — including (but not limited to) LIVE Gifts, LIVE Match, and time-based Ranking features." Há direito de recurso. As restrições ficam em *Creator tools › LIVE Center › Rules and guidance › Restrictions*.
- **Fiscalização:** "using a combination of technology and moderators", ou seja, sistemas automáticos mais pessoas.

## 1.2 Diretrizes da Comunidade para criadores de LIVE (LIVE Studio, atualizada em 11/02/2025)
- "Streaming previously recorded content in TikTok LIVE is not recommended."
- "Creators are recommended to be **present and on screen** throughout the duration of the LIVE."
- "Using a no-face effect might be easily regarded as low interaction, and LIVE might be regarded as a recording."
- "**Make sure your face is visible on camera.** If you need to step away, **pause the LIVE**" (o botão "Pause LIVE" fica dentro do botão de encerrar).
- "Stepping away from the LIVE for too long may be perceived as a lack of engagement."
- "Do not have a blank screen", "Do not display QR codes", "Do not show information from a third-party platform".
- Direitos autorais: "do not use videos in LIVE, including screen capturing, TV casting".
- "**Do not promote gambling**."

## 1.3 Diretrizes da Comunidade (versão de 25/08/2026, em vigor desde 24/09/2026)
- "**You must be 18 and older to go LIVE and to send gifts** to a creator during a LIVE session."
- Repetir conteúdo que não atende aos padrões do feed "Para Você" pode limitar a visibilidade da live e alguns recursos.
- "Streaming someone else's content without permission may also lead to restrictions."
- Jogo de azar e sorteio entram em "Regulated Goods, Services, and Commercial Activities". O TikTok Shop publicou em 2026 uma política que proíbe explicitamente "raffles, lotteries, wagers, sweepstakes, lucky draws, spin wheels" e "pagar por uma chance de ganhar" em lives. Essa informação vem de reportagens de terceiros que citam a política.

## 1.4 Termos de Serviço: o uso de bibliotecas "não oficiais"
Os Termos (versão para o Brasil, regida pela lei brasileira) proíbem:
> "use automated scripts to collect information from or otherwise interact with the Services"

O TikTok **não tem API oficial** para ler presentes e comentários da live em tempo real. O próprio README da biblioteca TikTokLive diz: "TikTok does not offer a public official API for reading livestream events."
Todas as ferramentas do mercado, incluindo TikFinity, TikTool, Euler Stream e os jogos vendidos, usam engenharia reversa.
**Na prática** esse uso é muito difundido (o TikFinity se apresenta como a ferramenta mais popular para TikTok LIVE), mas é tecnicamente uma área cinzenta.
**Como reduzimos o risco:** o protótipo só **lê** eventos. Ele não envia mensagens, curtidas, seguidas nem nada em nome da conta, e não automatiza ações da conta.

## 1.5 Matriz de risco

| Cenário | Pode ficar no ar? | Recebe presentes? | Risco para a conta |
|---|---|---|---|
| Jogo automático 24h, **sem ninguém**, sem câmera | Provavelmente, por um tempo | **Alto risco de desmonetização** (bate nas regras "automated… without meaningful creator presence" e "unattended") | Restrição de presentes, que pode ser permanente |
| Jogo automático + **avatar/voz de IA** no lugar da pessoa | Idem | **Alto risco**: a regra cita "AI" textualmente | Idem |
| Vídeo gravado ou em loop fingindo ao vivo | Risco de ser derrubado | Desmonetizado | Alto |
| **Jogo automático + pessoa presente** (você ou apresentador), interagindo | Sim | **Sim**: é o formato que a regra quer | Baixo, se seguir o resto das regras |
| Jogo automático + presença em blocos + **pausa ou desligamento** quando não há ninguém | Sim | Sim, nos blocos com presença | Baixo |

## 1.6 Riscos legais no Brasil (não é aconselhamento jurídico)
- **Jogo de azar:** dar prêmio de valor (dinheiro, Pix, produto) definido por sorte em troca de presente é jogo de azar. Explorar jogo de azar é contravenção penal (Lei de Contravenções Penais, art. 50) e também é proibido pelo TikTok. **Nosso jogo não paga prêmio real nenhum.** O "prêmio" é só reconhecimento no placar.
- **Imposto:** o dinheiro sacado do TikTok é renda. Vindo do exterior para pessoa física, entra em regra no carnê-leão; também dá para faturar como MEI ou ME, conforme o caso. **Fale com um contador** antes de escalar.
- **Direitos autorais:** use apenas músicas, sons e artes livres de direitos ou licenciados.
- **Imagem de terceiros:** apresentadores contratados precisam de contrato com autorização de uso de imagem.
