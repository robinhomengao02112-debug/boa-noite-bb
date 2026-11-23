# 07 — Plano de execução e checklists

## Fase 0: conta (de 0 a 1 semana, ou mais se faltar seguidores)
- [ ] Titular da conta com **18 anos ou mais**: obrigatório para LIVE e presentes
- [ ] Conta com **≈ 1.000 seguidores**. Se faltar, poste os cortes ou trechos do jogo como vídeos (o simulador ajuda a gerar o conteúdo)
- [ ] Nome e visual da marca do canal (não precisa ser o seu rosto pessoal se houver apresentadores)
- [ ] Baixar o **TikTok LIVE Studio** e conferir o **acesso de LIVE** (canto superior direito)
- [ ] Ver se a conta tem **chave de transmissão** (RTMP) para usar o OBS
- [ ] Ler o arquivo 01 inteiro

## Fase 1: montar no PC (1 dia)
- [ ] Dar dois cliques no **`INSTALAR.bat`** (instala e configura tudo; ver `COMO-USAR.md`)
- [ ] Abrir o atalho **Painel da Live** e ver o jogo no OBS (o instalador já configura o OBS)
- [ ] (Opcional) Adicionar webcam ou microfone na cena "Jogo" do OBS
- [ ] (Opcional) Criar uma conta grátis na **Euler Stream** e colocar a chave em `%USERPROFILE%\LiveTikTok\config.json` → `eulerApiKey`

## Fase 2: primeira live de teste (1 semana)
- [ ] Colar o Servidor e a Chave do LIVE Center no painel e clicar em **LIGAR LIVE**
- [ ] Conferir que comentários de 1 a 4 entram nos times
- [ ] Conferir no painel (card "Hoje") que os presentes estão sendo registrados
- [ ] Fazer lives de 1–2 horas **com você presente** em horários diferentes
- [ ] Anotar: espectadores médios, pico, presentes, horários

## Fase 3: otimizar (semanas 2 a 4)
- [ ] Olhar o card **Aprendizado automático** do painel (o rodízio já prioriza sozinho os jogos que rendem mais) e os arquivos `data/ledger-*.jsonl` (horários e presentes)
- [ ] Ajustar os pontos e efeitos em `config.json` (presentes baratos devem ter efeito visível)
- [ ] Testar 1–2 jogos de pacote pronto (arquivo 06) para ver o que o público curte
- [ ] **Preencher `09-ficha-do-seu-jogo.md`**, e eu implemento o seu jogo
- [ ] Postar de 2 a 3 cortes por dia

## Fase 4: escala (mês 2 em diante)
- [ ] Definir a grade fixa de horários (o TikTok recomenda ter uma agenda)
- [ ] Usar o campo **"Desligar sozinho às"** do painel para encerrar no horário
- [ ] Contratar apresentadores **só para horários que já se pagam** (arquivo 03), de preferência ganhando porcentagem do turno
- [ ] Contrato com o apresentador: uso de imagem, roteiro (arquivo 02), proibições (arquivo 05)
- [ ] Rodar a liga semanal e lançar jogos novos a cada 2–4 semanas para manter a novidade
- [ ] Conversar com um contador sobre a tributação

## Checklist diário (2 minutos)
- [ ] Painel da Live: "AO VIVO" e "Conectado"
- [ ] O som e a câmera do apresentador estão ok
- [ ] Ontem: ver `http://127.0.0.1:3000/api/stats?day=AAAA-MM-DD` e anotar numa planilha
- [ ] Ver o **LIVE Center › Restrictions** no app: não pode haver nenhuma restrição
- [ ] Espaço em disco para as gravações

## Checklist semanal
- [ ] Baixar a versão nova do projeto e rodar o `INSTALAR.bat` de novo (atualiza e mantém as suas configurações)
- [ ] Atualizar o Windows e os drivers **fora** do horário da live
- [ ] Revisar quais jogos e efeitos renderam mais; ajustar
- [ ] Fazer backup da pasta `data/`

## Sinais de alerta → o que fazer
| Sinal | Ação |
|---|---|
| Aviso de restrição de monetização no inbox | Parar e ler o motivo. Recorrer pelo inbox. Revisar o que mudou (presença, textos, efeitos). |
| Jogo reiniciando várias vezes por hora | Ver `data/controlador.log`. Rodar o `INSTALAR.bat` de novo (atualiza). O TikTok pode ter mudado o protocolo. |
| `silentForSeconds` alto com a live no ar | A conexão de leitura caiu. Ver a internet e o limite da Euler Stream (a chave grátis aumenta o limite). |
| Espectadores caindo | Trocar de jogo, aumentar a interação do apresentador, testar outro horário |
