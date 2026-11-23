# Prompt para o Claude Code no seu PC (Windows)

**Como usar:** abra o **Claude Code no seu computador** (app Claude Desktop → Claude Code, ou `claude` num terminal), troque `SEU_USUARIO_DO_TIKTOK` pelo seu @ sem o @ e cole tudo o que está entre as linhas `=====`.

=====

Você está no meu PC com Windows. Sua tarefa é **instalar, configurar, testar e deixar rodando** a minha live interativa automática do TikTok. Trabalhe com autonomia, verifique cada etapa antes de seguir, não invente resultados e, se algo falhar, diagnostique e corrija. Só me chame quando for realmente indispensável: aprovar uma janela do Windows (UAC), colar a chave da live ou fazer login em algum lugar.

Meu usuário do TikTok: **SEU_USUARIO_DO_TIKTOK**

## 1. Contexto (o que já foi pesquisado e construído)
O projeto está pronto no GitHub:
- Repositório: `https://github.com/robinhomengao02112-debug/boa-noite-bb`
- Branch: **`ccr-7c64fc02-cqdem9`**
- Pasta do projeto: **`live-tiktok-automatica/`**. Comece lendo `README.md`, `COMO-USAR.md` e `starter/README.md`.

Como o sistema funciona:
- **Jogos** (Node.js + página web 1080x1920 como fonte de navegador do OBS): `chefao` (Raid do Chefão, cooperativo), `corrida` (Corrida de Elementos, 4 times) e `melancia` (Estoura ou Não, suspense).
- Os espectadores participam de graça (comentar e curtir), e os presentes dão efeitos maiores. O nome de quem participa aparece na tela.
- **Rodízio inteligente:** troca de jogo a cada 30 min, sempre entre rodadas. Mede as moedas por minuto de cada jogo e prioriza os que rendem mais, sem deixar de testar os outros 20% das vezes.
- **Trilha sonora original** gerada no navegador (Web Audio, sem arquivos e sem direitos autorais), com efeitos sonoros.
- **Leitura da live:** biblioteca `tiktok-live-connector` 2.5.0, não oficial e só de leitura, com assinatura pela Euler Stream (plano grátis). Ela só conecta quando a live está no ar; antes disso, `UserOfflineError` é normal e o sistema tenta de novo sozinho.
- **Controlador 24h** (`starter/src/controller.js`), com painel em `http://127.0.0.1:3001`:
  - mantém o jogo e o OBS de pé;
  - configura o OBS por obs-websocket (perfil vertical 1080x1920 com saída 720x1280 a 30 fps e 3000 kbps, reconexão automática, cena "Jogo" com a fonte "Jogo (overlay)" apontando para `http://127.0.0.1:3000/`);
  - liga e desliga a transmissão, religa após quedas do OBS, do jogo ou do PC, e desliga no horário marcado.
- **Instalador:** `live-tiktok-automatica\starter\scripts\instalar.ps1`. Ele:
  - instala o Node.js LTS e o OBS via winget;
  - copia o projeto para `<HOME>\LiveTikTok` (numa atualização, mantém o `config.json` existente);
  - roda `npm ci`;
  - roda `scripts\setup.js` com o OBS fechado (ativa o obs-websocket com senha; grava `FirstRun=true`, `ConfirmOnExit=false`, `BrowserHWAccel=false` e `ConfigOnNewProfile=false`);
  - desliga a suspensão do PC na tomada;
  - cria os atalhos (inicialização do Windows, Painel, LIGAR, DESLIGAR) e inicia o controlador.
  Aceita `-Usuario <nome>` e `-SemPausa` para rodar sem perguntas.

Já foi testado com o OBS 32.2 real (em Linux): configuração do zero sem nenhuma janela, transmissão RTMP com vídeo e áudio e 0 quadros perdidos, volta ao ar após queda do OBS (cerca de 20 s), do jogo e do "PC reiniciado", desligamento por horário e rodízio inteligente.
**Ainda NÃO foi testado no Windows** (scripts `.ps1`, `.bat` e `.vbs`). Esse é o seu trabalho principal: validar no Windows real e corrigir o que precisar.

## 2. Regras que você NÃO pode quebrar
1. **Não automatize o login do TikTok** nem a obtenção da chave de transmissão, e não envie mensagens, curtidas ou seguidas pela minha conta. Os Termos do TikTok proíbem scripts automáticos interagindo com o serviço. A biblioteca do projeto só lê eventos; mantenha assim.
2. **A chave muda a cada live.** Eu pego a chave em *TikTok LIVE Center → LIVE Producer* e colo **eu mesmo** no painel. Não peça para eu colar a chave no chat e não a escreva em lugar nenhum além do painel.
3. **Nunca** suba para o GitHub o `config.json` com senha nem nada da pasta `data\` (o `estado.json` guarda a chave).
4. Sem música ou vídeo com direitos autorais, sem sorteio ou prêmio real em troca de presentes, sem política nos jogos e sem textos pedindo presente repetidamente. São regras de monetização do TikTok.
5. Não desligue o antivírus nem o firewall, e não mude configurações do Windows além das que o instalador já faz (energia).
6. **Aviso que eu já conheço:** a regra oficial de monetização de LIVE pode cortar os presentes de lives "operadas principalmente por sistemas automáticos sem presença significativa do criador". O sistema roda sozinho, mas no relatório final me lembre de adicionar a webcam na cena "Jogo" para os horários de pico. Não precisa repetir isso mais de uma vez.

## 3. Execução passo a passo (verifique cada um antes de seguir)
**Passo 1: pré-requisitos**
- Confirme o Windows 10/11 e o `winget --version`. Se o winget não existir, me peça para atualizar o "Instalador de Aplicativos" na Microsoft Store.
- Veja se o `git` existe. Se não existir, instale com `winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements` e atualize o PATH da sessão.

> Nos caminhos abaixo, `<HOME>` é a minha pasta de usuário (`C:\Users\<eu>`). Use a sintaxe certa do shell que você estiver usando: `$env:USERPROFILE` no PowerShell, `%USERPROFILE%` no cmd, `$USERPROFILE` ou `~` no Git Bash.

**Passo 2: baixar o projeto**
- `git clone --branch ccr-7c64fc02-cqdem9 --single-branch https://github.com/robinhomengao02112-debug/boa-noite-bb.git "<HOME>\Downloads\boa-noite-bb"`
- Se pedir login (repositório privado), use o login do GitHub que eu tiver neste PC (gh/credencial). Se não houver, me peça para baixar o ZIP do branch pelo navegador e me diga a pasta onde extrair.
- Confirme que existe `live-tiktok-automatica\starter\scripts\instalar.ps1`.

**Passo 3: instalar sem perguntas**
- Rode no PowerShell:
  `powershell -NoProfile -ExecutionPolicy Bypass -File "<HOME>\Downloads\boa-noite-bb\live-tiktok-automatica\starter\scripts\instalar.ps1" -Usuario SEU_USUARIO_DO_TIKTOK -SemPausa`
- Se aparecer UAC (instalação do Node.js ou do OBS), avise-me para clicar em **Sim**.
- Leia toda a saída. Termina em "PRONTO"? Se der erro, corrija e rode de novo (o script é seguro para repetir).

**Passo 4: verificar a instalação**
- `Invoke-RestMethod http://127.0.0.1:3001/api/status` deve responder.
- Confira:
  - `game.health.game` = um dos jogos;
  - `stream.connected` = true (o OBS foi aberto e está conectado ao controlador);
  - `config.user` = o meu usuário.
- Leia `<HOME>\LiveTikTok\data\controlador.log`. Precisa ter "[obs] configuração do OBS ok".
- Confirme no OBS (por print de tela, se você puder ver a tela; se não, me pergunte) que a cena **"Jogo"** mostra o jogo **e não está preta**, e que **não há janela aberta** (assistente de configuração, "Modo de Segurança" ou confirmação).
- Confirme que existem os atalhos "Painel da Live", "LIGAR LIVE", "DESLIGAR LIVE" e "Iniciar controlador da Live" na Área de Trabalho, e "Live TikTok - controlador" em `shell:startup`.

**Passo 5: teste sem live (simulador)**
- Rode o `<HOME>\LiveTikTok\scripts\parar-tudo.bat`, depois `node src\controller.js --simulate` dentro de `<HOME>\LiveTikTok`, em segundo plano.
- Em `http://127.0.0.1:3000/`, os jogos precisam se mexer, mostrar nomes e tocar música.
- Troque de jogo com `Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/jogo -ContentType 'application/json' -Body '{"id":"corrida"}'` e confira que o OBS acompanha a troca.
- Encerre o simulador e volte o controlador normal com `wscript "<HOME>\LiveTikTok\scripts\iniciar-oculto.vbs"`.
- Os dados do simulador ficam em arquivos separados (`*-simulador*`) e não contaminam o aprendizado real.

**Passo 6: primeira live real**
- Peça para eu abrir o **Painel da Live**, colar o **Servidor** e a **Chave** do *TikTok LIVE Center → LIVE Producer* e clicar em **LIGAR LIVE**.
  - Se a minha conta **não tiver** acesso à chave (LIVE Producer), use o modo **LIVE Studio**: troque no painel; no TikTok LIVE Studio, adicione uma fonte Link/Navegador `http://127.0.0.1:3000/` em 1080x1920 e clique em Go LIVE.
- Acompanhe `/api/status` até `stream.streaming = true` e `game.health.connected = true` (o jogo conecta à live em até 1 minuto).
- Se o painel disser "Não consegui conectar…", a chave provavelmente expirou: peça uma nova.
- Confira que comentários na live (ex.: "1") aparecem no jogo e que `stream.skippedFrames` fica perto de 0. Se pular quadros, baixe `obs.videoBitrate` no `config.json` (ex.: 2500) e reinicie o controlador.

**Passo 7: teste de resistência (com a live no ar e minha autorização)**
- Feche o OBS à força (`Stop-Process -Name obs64 -Force`). Em até cerca de 60 s, ele deve reabrir **sem a janela de Modo de Segurança** e voltar AO VIVO. Confirme pelo `controlador.log`.
- Clique em DESLIGAR, confirme que a transmissão parou, e ligue de novo.

## 4. Diagnóstico rápido (problemas já conhecidos e a solução)
| Sintoma | Causa | Solução |
|---|---|---|
| Fonte do jogo **preta** no OBS | Aceleração de GPU do navegador | `BrowserHWAccel=false` em `%APPDATA%\obs-studio\global.ini` [General] (o setup faz isso; rode `node scripts\setup.js` com o OBS fechado) |
| Janela **"Run in Safe Mode?"** | O OBS caiu antes e deixou a marca em `%APPDATA%\obs-studio\.sentinel\` | O controlador apaga essa marca antes de abrir o OBS. Se aparecer mesmo assim, clique em *Normal Mode* e me avise |
| **Assistente de configuração** abre | `FirstRun` errado | `FirstRun=true` em `user.ini` e `global.ini` ([General]). No OBS, `true` = "já rodou" |
| "Sem conexão com o OBS" / autenticação | Senha do websocket diferente ou OBS aberto durante o setup | Feche o OBS e rode `node scripts\setup.js`. Confira em `%APPDATA%\obs-studio\plugin_config\obs-websocket\config.json` |
| OBS não encontrado | Instalado em outro lugar | Preencha `obs.path` no `config.json` com o caminho do `obs64.exe` |
| Porta 3000 ou 3001 em uso | Outro programa | Mude `port` e `panelPort` no `config.json` |
| `UserOfflineError` / "Failed to retrieve Room ID" | A live não está no ar ou o usuário está errado | Normal até a live começar. Confira `tiktokUsername` |
| Muitas quedas de leitura | Limite da Euler Stream | Crie uma chave grátis em eulerstream.com e coloque em `eulerApiKey` |
| `npm ci` falha | Internet ou Node antigo | Node ≥ 20. Rode de novo |
| Controlador não inicia ao ligar o PC | O Windows parou na tela de senha | Explique como ligar o login automático (`netplwiz`) e "Restore on AC Power Loss" na BIOS; **não** faça isso sem eu autorizar |

Se você corrigir algo no código, aplique em `<HOME>\LiveTikTok` **e** no clone do repositório. Depois me pergunte se quero que você faça commit e push no branch `ccr-7c64fc02-cqdem9`, sem nunca subir senhas, chaves nem a pasta `data\`.

## 5. Relatório final (em português, curto)
1. O que foi instalado e configurado.
2. Resultado de cada passo (✅ ou ❌), com a evidência: saída do `/api/status`, trecho do log ou print.
3. Problemas encontrados e como você corrigiu.
4. O que ficou pendente e depende de mim (ex.: colar a chave, login automático, webcam).
5. Como eu uso no dia a dia: Painel → colar a chave → LIGAR; DESLIGAR ou horário automático.

=====
