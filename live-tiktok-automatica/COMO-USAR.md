# COMO USAR: instalar e rodar no seu PC

## 1. Instalar (uma vez só, cerca de 10 minutos)
1. Baixe o projeto: no GitHub, entre no repositório `boa-noite-bb`, escolha o branch `ccr-7c64fc02-cqdem9` e clique em **Code → Download ZIP**.
2. **Extraia o ZIP inteiro** (botão direito → Extrair tudo).
3. Abra a pasta `live-tiktok-automatica` e dê **dois cliques em `INSTALAR.bat`**.
   - Se aparecer "O Windows protegeu o computador": clique em **Mais informações → Executar assim mesmo**.
   - Se o Windows pedir permissão de administrador (para instalar o Node.js e o OBS): clique em **Sim**.
   - Se o firewall perguntar sobre o OBS: pode clicar em **Cancelar**, porque o controle é só local.
4. Quando o instalador pedir, digite o seu **usuário do TikTok** (sem @).
5. No fim, o **Painel da Live** abre sozinho no navegador.

**O que o instalador faz sozinho:**
- instala o Node.js e o OBS, se faltarem;
- copia tudo para `C:\Users\<você>\LiveTikTok`;
- configura o OBS: perfil vertical, cena do jogo, reconexão automática, controle remoto, e desliga as janelas que travariam o modo automático;
- tira o PC do modo de suspensão quando está na tomada;
- cria os atalhos e deixa o **controlador iniciando junto com o Windows**.

## 2. Ir ao vivo
**No modo OBS (o padrão), tudo é automático:**
1. Pegue o **Servidor** e a **Chave**: TikTok **LIVE Center → LIVE Producer**. A chave do TikTok **muda a cada live**.
2. No **Painel da Live**, cole os dois e clique em **▶ LIGAR LIVE**.
3. Pronto. O jogo roda sozinho, troca de jogo sozinho e aprende sozinho qual jogo rende mais.
4. Para encerrar, clique em **■ DESLIGAR LIVE** (ou no atalho "DESLIGAR LIVE"). Também dá para preencher **"Desligar sozinho às"** antes de ligar.

**No modo TikTok LIVE Studio (se sua conta não tiver chave):**
1. No painel, escolha o modo **TikTok LIVE Studio**.
2. No LIVE Studio, adicione uma vez a fonte **Link/Navegador** com `http://127.0.0.1:3000/` em 1080 x 1920.
3. Clique em **Go LIVE** no LIVE Studio. O jogo conecta sozinho em até 1 minuto.

## 3. O que acontece sozinho

| Situação | O que o sistema faz |
|---|---|
| PC ligou ou reiniciou | O controlador inicia junto com o Windows. Se a live estava ligada, ele **tenta voltar ao ar** com a última chave. Se o TikTok já tiver encerrado aquela live, o painel pede uma chave nova. |
| OBS fechou ou travou | Reabre o OBS **sem a janela de Modo de Segurança**, reconfigura e volta a transmitir. Testado: voltou ao ar em cerca de 20 s. |
| Internet oscilou | O OBS reconecta sozinho (até 100 tentativas). |
| Jogo travou | O controlador reinicia o jogo em cerca de 3 s. |
| A cada 30 minutos | Troca de jogo, sempre entre rodadas. |
| Aprendizado | Mede as **moedas por minuto** de cada jogo nas lives reais e dá mais tempo de tela aos que rendem mais. Mesmo assim, continua testando os outros 20% das vezes, porque o público muda. |
| Horário definido | Desliga a live na hora marcada. |

## 4. Atalhos na Área de Trabalho
- **Painel da Live**: o controle de tudo (`http://127.0.0.1:3001`).
- **LIGAR LIVE** / **DESLIGAR LIVE**: ligar e desligar direto, usando a última chave guardada.
- **Iniciar controlador da Live**: se você tiver fechado o controlador.

## 5. Para rodar de verdade sem ninguém por perto
- **Login automático no Windows:** sem ele, depois de uma queda de energia o PC para na tela de senha e nada inicia. Para ligar: tecla Windows + R → `netplwiz` → desmarque "Os usuários devem digitar um nome de usuário e uma senha". Só faça isso se o PC estiver num lugar seguro.
- **BIOS → "Restore on AC Power Loss: Power On"**, para o PC religar sozinho quando a energia voltar.
- **Nobreak** e **internet por cabo**.
- **Windows Update:** ajuste o "Horário ativo" para o horário da live, para ele não reiniciar o PC no meio.

## 6. Lembrete importante sobre o dinheiro
As regras oficiais do TikTok podem **cortar os presentes** de lives "operadas principalmente por sistemas automáticos sem presença significativa do criador" (detalhes no arquivo 01). O sistema roda sozinho, mas para proteger os presentes:
- apareça na câmera nos horários de pico (adicione a webcam na cena "Jogo" do OBS uma vez);
- acompanhe **LIVE Center → Restrições** no app.

## 7. Atualizar ou desinstalar
- **Atualizar:** baixe a versão nova e rode o `INSTALAR.bat` de novo. As suas configurações e dados são mantidos.
- **Parar tudo:** `C:\Users\<você>\LiveTikTok\scripts\parar-tudo.bat`.
- **Desinstalar:** rode o `parar-tudo.bat`, apague a pasta `LiveTikTok` e os atalhos (Área de Trabalho e `shell:startup`).

## 8. Onde ficam os registros
`C:\Users\<você>\LiveTikTok\data\`
- `controlador.log`: tudo o que aconteceu (liga, desliga, quedas, religamentos);
- `ledger-AAAA-MM-DD.jsonl`: cada presente recebido;
- `aprendizado.json`: quanto cada jogo rendeu por minuto;
- `estado.json`: a última chave e o estado da live. **Não compartilhe este arquivo**, porque ele tem a sua chave.
