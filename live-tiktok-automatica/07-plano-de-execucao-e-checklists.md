# 07 — Plano de execução e checklists

## Fase 0: conta (de 0 a 1 semana, ou mais se faltar seguidores)
- [ ] Titular da conta com **18 anos ou mais**: obrigatório para LIVE e presentes
- [ ] Conta com **≈ 1.000 seguidores**. Se faltar, poste os cortes ou trechos do jogo como vídeos (o simulador ajuda a gerar o conteúdo)
- [ ] Nome e visual da marca do canal (não precisa ser o seu rosto pessoal se houver apresentadores)
- [ ] Baixar o **TikTok LIVE Studio** e conferir o **acesso de LIVE** (canto superior direito)
- [ ] Ver se a conta tem **chave de transmissão** (RTMP) para usar o OBS
- [ ] Ler o arquivo 01 inteiro

## Fase 1: montar no PC (1 dia)
- [ ] Instalar o **Node.js 20 ou mais novo** (nodejs.org, versão LTS)
- [ ] Copiar a pasta `starter/`, colocar o seu usuário em `config.json` → `tiktokUsername`
- [ ] Rodar `scripts\simular-windows.bat` e ver o jogo funcionando com eventos falsos
- [ ] Instalar o OBS ou o LIVE Studio e adicionar a **Fonte de navegador** `http://localhost:3000/` em 1080x1920
- [ ] Testar a webcam e o microfone na cena
- [ ] (Opcional) Criar uma conta grátis na **Euler Stream** e colocar a chave em `config.json` → `eulerApiKey`

## Fase 2: primeira live de teste (1 semana)
- [ ] Iniciar a live, depois rodar `scripts\iniciar-windows.bat` (ele conecta na live que já está no ar)
- [ ] Conferir que comentários de 1 a 4 entram nos times
- [ ] Conferir em `http://localhost:3000/api/stats` que os presentes estão sendo registrados
- [ ] Fazer lives de 1–2 horas **com você presente** em horários diferentes
- [ ] Anotar: espectadores médios, pico, presentes, horários

## Fase 3: otimizar (semanas 2 a 4)
- [ ] Analisar os arquivos `data/ledger-*.jsonl`: quais **horários** e quais **presentes** rendem
- [ ] Ajustar os pontos e efeitos em `config.json` (presentes baratos devem ter efeito visível)
- [ ] Testar 1–2 jogos de pacote pronto (arquivo 06) para ver o que o público curte
- [ ] **Preencher `09-ficha-do-seu-jogo.md`**, e eu implemento o seu jogo
- [ ] Postar de 2 a 3 cortes por dia

## Fase 4: escala (mês 2 em diante)
- [ ] Definir a grade fixa de horários (o TikTok recomenda ter uma agenda)
- [ ] Automação de horário: Agendador de Tarefas + `obs64.exe --startstreaming` + parada via obs-websocket
- [ ] Contratar apresentadores **só para horários que já se pagam** (arquivo 03), de preferência ganhando porcentagem do turno
- [ ] Contrato com o apresentador: uso de imagem, roteiro (arquivo 02), proibições (arquivo 05)
- [ ] Rodar a liga semanal e lançar jogos novos a cada 2–4 semanas para manter a novidade
- [ ] Conversar com um contador sobre a tributação

## Checklist diário (2 minutos)
- [ ] `http://localhost:3000/health` → `connected: true`
- [ ] O som e a câmera do apresentador estão ok
- [ ] Ontem: ver `api/stats?day=…` e anotar numa planilha
- [ ] Ver o **LIVE Center › Restrictions** no app: não pode haver nenhuma restrição
- [ ] Espaço em disco para as gravações

## Checklist semanal
- [ ] `npm update` dentro de `starter/` (atualiza a biblioteca de conexão) e teste no simulador
- [ ] Atualizar o Windows e os drivers **fora** do horário da live
- [ ] Revisar quais jogos e efeitos renderam mais; ajustar
- [ ] Fazer backup da pasta `data/`

## Sinais de alerta → o que fazer
| Sinal | Ação |
|---|---|
| Aviso de restrição de monetização no inbox | Parar e ler o motivo. Recorrer pelo inbox. Revisar o que mudou (presença, textos, efeitos). |
| Supervisor reiniciando várias vezes por hora | Ver `data/supervisor.log`. Rodar `npm update`. O TikTok pode ter mudado o protocolo. |
| `silentForSeconds` alto com a live no ar | A conexão de leitura caiu. Ver a internet e o limite da Euler Stream (a chave grátis aumenta o limite). |
| Espectadores caindo | Trocar de jogo, aumentar a interação do apresentador, testar outro horário |
