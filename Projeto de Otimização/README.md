# Projeto de Otimização

Otimizador **seguro e 100 % reversível** para notebooks com **Intel HD Graphics 620** (e UHD 620),
feito para tirar o máximo de desempenho em jogos sem perder qualidade de imagem e sem arriscar o
computador.

- `INICIAR.bat` — abre o aplicativo (pede permissão de administrador).
- `Otimizador.ps1` — o aplicativo (Windows PowerShell 5.1 + interface WPF, nada para instalar).
- `PESQUISA.md` — a pesquisa completa sobre o hardware, o que foi feito e por quê.
- `Dados\` — criada na primeira execução: backups, relatórios, testes e logs.

## Como instalar no seu notebook

1. Baixe o projeto: **<https://github.com/robinhomengao02112-debug/boa-noite-bb/archive/refs/heads/ccr-10652a12-hlun9n.zip>**
   (ou no GitHub: branch `ccr-10652a12-hlun9n` → **Code → Download ZIP**).
2. Antes de extrair: clique com o botão direito no ZIP → **Propriedades** → marque **Desbloquear** → OK.
3. Extraia e copie a pasta **`Projeto de Otimização`** para onde preferir (ex.: Área de Trabalho ou `C:\`).
4. Dê dois cliques em **`INICIAR.bat`** e clique em **Sim** na janela do Windows.
   Se o Windows mostrar "O Windows protegeu o computador", clique em *Mais informações → Executar assim mesmo*.

Requisitos: Windows 10 ou 11 (o PowerShell 5.1 já vem no Windows).

## Passo a passo recomendado

1. **Diagnóstico** — abre sozinho e mostra modelo, processador, cada pente de RAM (e se está em
   single ou dual-channel), disco, driver da Intel, bateria, temperatura e uma lista de
   recomendações **para o seu computador**, por prioridade. "Salvar relatório" gera um `.txt`.
2. **Testes** — com o carregador ligado, rode o teste com o rótulo **Antes**. Se quiser, meça o FPS
   de 1–3 jogos (RTSS/CapFrameX) e registre.
3. **Otimizações** — "Aplicar selecionadas" (as recomendadas já vêm marcadas). O app cria um
   **ponto de restauração** e guarda os valores originais antes de mudar qualquer coisa.
   **Reinicie** o computador.
4. **Testes** de novo com o rótulo **Depois** → "Ver histórico e comparação".
5. **Jogos** — antes de jogar, abra o jogo e use **Turbo agora**. Siga o guia de configurações e
   use os links para driver da Intel e ferramentas.

## Segurança

- Cada alteração salva o valor original em `Dados\Backups\estado.json`.
- **Restaurar → Desfazer TODAS** volta tudo exatamente como era (inclusive o plano de energia).
- Ponto de restauração do Windows como segunda camada de proteção.
- O app **nunca**: desativa antivírus/firewall/atualizações/serviços, mexe em BIOS, tensão ou
  overclock, apaga cache de shaders ou desativa a memória virtual.

## O que mais aumenta o desempenho (resumo da pesquisa)

1. **2º pente de RAM igual (dual-channel)** — o maior ganho possível na HD 620.
2. Jogar **na tomada** + plano de energia do app.
3. **Driver Intel** 31.0.101.21xx (série legada oficial para 7ª–10ª geração).
4. **720p / 768p** e configurações baixas/médias nos jogos.
5. **SSD** no lugar de HD mecânico.
6. **Limpeza interna + pasta térmica** se o notebook esquenta.

Detalhes, números e fontes em [`PESQUISA.md`](PESQUISA.md).
