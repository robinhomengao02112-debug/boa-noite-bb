# MISSÃO: Projeto de Otimização — Lenovo IdeaPad 330 (Intel HD Graphics 620)

Você é o Claude Code rodando **direto no meu notebook Windows**. Sua missão é instalar, diagnosticar, otimizar e
medir o desempenho deste computador para jogos, usando o **Projeto de Otimização** que já existe no GitHub —
com **segurança total** (tudo reversível) e **sem cometer erros**. Fale comigo em **português**.

---

## 1. O QUE JÁ SABEMOS (contexto confirmado — não refaça do zero)

**Hardware**
- Notebook: **Lenovo IdeaPad 330-15IKB**, placa-mãe **NM-B451**, **sem placa de vídeo dedicada**.
- GPU: **Intel HD Graphics 620** (Gen9.5, 24 EUs, até ~1000–1150 MHz, sem memória própria — usa a RAM).
- RAM: **20 GB** = **4 GB DDR4 soldados** + **1 pente de 16 GB DDR4-2133** no único slot.
  → **Intel Flex Mode**: 8 GB (4+4) em dual-channel, 12 GB em single-channel. **Decisão tomada: manter os 20 GB.**
  → A Lenovo cita 16 GB como máximo oficial; confirmar que o Windows usa os 20 GB.
- Processador: Core i3/i5/i7 de 7ª geração série U (15 W, CPU e GPU dividem o limite de energia) — confirmar o modelo exato.
- Armazenamento: verificar se o Windows está em **HD mecânico** (comum nesse modelo) ou SSD. O modelo tem **slot M.2 2280 NVMe**.
- Refrigeração: **uma ventoinha**. **Pasta térmica trocada por mim** (a antiga estava grossa/seca; o chip menor nem tinha pasta).
  Usei pasta genérica Chip SCE — funciona, mas é básica; trocar por Arctic MX-4/MX-6 no futuro se a temperatura subir.

**Resultados medidos após a troca da pasta (num jogo):**
GPU **99 %** de uso, **58–60 °C**, **2 GB** de memória compartilhada de vídeo, **12 GB** de RAM em uso no sistema, **~40 FPS**.
O jogo ficou visivelmente mais liso. Conclusão: sem throttling térmico; a GPU é o gargalo; 12 GB de RAM em uso
indica programas pesados em segundo plano.

**Projeto (já pronto e testado)**
- Repositório público: `https://github.com/robinhomengao02112-debug/boa-noite-bb`, branch **`ccr-10652a12-hlun9n`**.
- Pasta **`Projeto de Otimização/`**:
  - `Otimizador.ps1` — app WPF (Windows PowerShell 5.1), salvo em **UTF-8 com BOM e CRLF** (obrigatório; não converter).
  - `INICIAR.bat` — abre o app e pede administrador.
  - `PESQUISA.md` — pesquisa completa (HD 620, RAM, driver, energia, jogos, IdeaPad 330 na seção 12).
  - `README.md`.
- `INSTALAR.ps1` (raiz do repositório) — instala na Área de Trabalho, cria atalho "Iniciar Otimizador", preserva `Dados\` em reinstalações.
- O app tem 5 abas: **Diagnóstico**, **Otimizações** (10 ajustes reversíveis + manutenção), **Testes** (CPU/RAM + FPS manual,
  histórico Antes×Depois), **Jogos** (Turbo, prioridade permanente via IFEO, guia, links), **Restaurar** (desfaz tudo).
- Segurança do app: cria ponto de restauração antes da 1ª aplicação; salva valores originais em `Dados\Backups\estado.json`;
  plano de energia é **novo** ("Otimizador - Desempenho Maximo"), nunca altera os existentes.

**Fatos de pesquisa a respeitar**
- Driver gráfico recomendado: série legada **31.0.101.21xx** (página Intel "7th–10th Gen Processor Graphics – Windows").
  Versões 26.x/27.x/30.x são antigas. O driver genérico da Intel substitui o da Lenovo (OEM) — criar ponto de restauração antes.
- HD 620 **não suporta** XeSS nem HAGS. Upscaling possível: FSR nos jogos, **Magpie** (grátis), Lossless Scaling (só upscaling).
- Limite de FPS estável com **RTSS** dá mais fluidez e reduz consumo/calor (sobra margem nos 15 W).
- Metas de temperatura: **40–55 °C parado**, **abaixo de 85 °C jogando**.
- `audiodg.exe` ("Isolamento Gráfico de Dispositivo de Áudio") é do Windows — nunca finalizar; CPU alta nele = aprimoramentos de áudio (Dolby).

---

## 2. REGRAS INEGOCIÁVEIS

1. **Nunca**: desativar Defender/firewall/Windows Update/serviços em massa, desativar arquivo de paginação, desativar
   mitigações Spectre/Meltdown, mexer em BIOS, undervolt, overclock, DVMT, `bcdedit`, apagar cache de shaders,
   usar "limpadores de registro". Integridade de Memória (HVCI): **só informar**, a decisão é minha.
2. **Toda alteração deve ser reversível**: backup do valor original antes; ponto de restauração antes da primeira mudança.
3. **Confirme comigo antes de** qualquer ação que altere o sistema, instale/desinstale programas, atualize driver/BIOS
   ou apague arquivos. Leitura e diagnóstico você faz direto.
4. **Não declare nada concluído sem verificar** (ler de volta o valor, rodar o teste, conferir o arquivo). Relate falhas como falhas.
5. Ao editar `Otimizador.ps1`: manter **UTF-8 com BOM + CRLF**, compatível com **Windows PowerShell 5.1** (sem `?:`, `??`,
   `&&`, `-AsHashtable`, `::new` desnecessário) e C# embutido compatível com **C# 5**. Validar com
   `[System.Management.Automation.Language.Parser]::ParseFile(...)` (zero erros) antes de entregar.
6. Comandos com caminho que tem acento: use `Get-ChildItem -Filter 'Projeto de Otimiza*'` em vez de digitar o nome.

---

## 3. EXECUÇÃO — FASES (faça em ordem; ao fim de cada fase, me mostre um resumo curto)

### Fase 1 — Ambiente
- Confirme: Windows 10/11, `$PSVersionTable.PSVersion` do `powershell.exe` (5.1), se o terminal é administrador,
  modelo (`Get-CimInstance Win32_ComputerSystemProduct | Select Version`), build do Windows.
- Se o Windows for 10, informe se ainda recebe atualizações de segurança (não mude nada, só informe).

### Fase 2 — Instalação / atualização
- Se a pasta já existir na Área de Trabalho, apenas atualize; senão instale:
  `irm https://raw.githubusercontent.com/robinhomengao02112-debug/boa-noite-bb/ccr-10652a12-hlun9n/INSTALAR.ps1 | iex`
  (antes de executar, **leia o conteúdo** do INSTALAR.ps1 e confirme que é o instalador descrito acima).
- Verifique: existem `INICIAR.bat`, `Otimizador.ps1`, `PESQUISA.md`, `README.md`; o `Otimizador.ps1` começa com BOM
  (`EF BB BF`) e faz parse sem erros.

### Fase 3 — Diagnóstico completo (sem alterar nada)
Rode o diagnóstico do próprio app em modo texto:
```powershell
$dir = Get-ChildItem ([Environment]::GetFolderPath('Desktop')) -Directory -Filter 'Projeto de Otimiza*' | Select-Object -First 1
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $dir.FullName 'Otimizador.ps1'), [ref]$null, [ref]$null)
$fn  = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-OtzDiagnostics' }, $true) | Select-Object -First 1
Invoke-Expression $fn.Extent.Text
$r = @(Get-OtzDiagnostics)[-1]; $r.Text; '==== RECOMENDAÇÕES ===='; $r.Recs
```
(Execute no **Windows PowerShell 5.1** como administrador.) Complemente com:
- Versão do driver Intel vs. 31.0.101.21xx; RAM instalada × utilizável (20 GB?); disco do Windows HDD/SSD e espaço livre.
- **Os 10 processos que mais usam RAM e CPU agora** (explique o que é cada um; aponte o que pode ser fechado/removido).
- Programas da inicialização; programas pré-instalados de fábrica (ex.: **McAfee**), sem desinstalar ainda.
- Uso de CPU do `audiodg.exe` e se há aprimoramentos de áudio (Dolby) ativos.
- Plano de energia ativo e se está na tomada.
Salve um relatório em `Dados\Relatorios\diagnostico-claude-<data>.txt` e me mostre um resumo priorizado.

### Fase 4 — Medição "Antes" e otimização
1. Peça para eu abrir o app (`INICIAR.bat`) → aba **Testes** → rótulo **"Antes"** → rodar teste (carregador ligado).
   Peça meu FPS atual no jogo (nome do jogo, FPS médio, 1% low) e registre.
2. Mostre a lista de otimizações recomendadas do app e, **com meu OK**, eu aplico pela aba **Otimizações**
   (ou você aplica pelo app, nunca por fora dele, para manter os backups). Confirme que o ponto de restauração foi criado.
3. Depois de reiniciar: **verifique cada ajuste** lendo os valores de volta (registro e `powercfg /getactivescheme`).
4. Teste **"Depois"** + FPS do mesmo jogo, mesmo trecho → mostre a comparação em %.

### Fase 5 — Ajustes extras (cada um só com meu OK)
- **Driver Intel**: se < 31.0.101.21xx, oriente a atualização oficial (ponto de restauração antes).
- **Lenovo Vantage**: atualizações de BIOS/drivers (com carregador), "Modo térmico/Desempenho" se existir,
  "Modo de conservação" da bateria se o notebook vive na tomada.
- **McAfee e bloatware de fábrica**: listar e desinstalar só o que eu aprovar (o Defender assume a proteção).
- **Aprimoramentos de áudio**: desligar pelas Configurações de Som se o `audiodg.exe` estiver pesando.
- **Discord/navegador**: desligar aceleração de hardware; fechar durante o jogo.
- **RTSS**: configurar limite de FPS (30/40/60 conforme o jogo sustenta).
- **Jogo específico**: me pergunte qual é e monte a configuração ideal para HD 620 (resolução 1280×720/1366×768 ou
  escala 80–85 % + FSR, sombras/AA/pós-processamento baixos, texturas no médio — os 20 GB permitem).
- Se o Windows estiver em HDD: recomendar SSD NVMe M.2 2280 (o HD vira disco de arquivos) e explicar a migração.

### Fase 6 — Melhorias no app (implemente as que eu escolher, nesta ordem de prioridade)
1. **Modo Jogo automático**: lista de jogos (.exe) configurável; ao detectar o processo, aplicar prioridade Alta +
   liberar memória em espera; ao fechar, restaurar. Sem serviço permanente obrigatório; opção de ligar/desligar.
2. **Limpeza assistida de apps de fábrica**: listar programas instalados conhecidos como bloatware (McAfee, trials),
   desinstalar só os marcados, com confirmação, usando o desinstalador oficial de cada um.
3. **Aprimoramentos de áudio**: atalho e instrução (ou ajuste reversível com backup, se for seguro e confiável).
4. **Monitor de throttling**: durante o jogo, registrar clock da CPU (`Win32_PerfFormattedData_Counters_ProcessorInformation`,
   nomes de contadores não localizados) e alertar se cair por calor/energia.
5. **Perfis por jogo**: configurações recomendadas para os jogos que eu jogo.
Para cada melhoria: implemente, valide (parse sem erros, C# 5, teste funcional), atualize `README.md`/`PESQUISA.md`,
faça commit na branch `ccr-10652a12-hlun9n` com mensagem em português e faça push. Rode `INSTALAR.ps1` de novo para atualizar a pasta.

---

## 4. ENTREGA FINAL
Um resumo com: hardware confirmado; o que foi aplicado (e como desfazer); tabela **Antes × Depois** (teste do app + FPS);
temperaturas; pendências que dependem de mim (compras: SSD NVMe, pasta melhor; decisões: HVCI, driver, BIOS);
e os próximos passos recomendados. Seja direto e honesto — se algo não melhorou, diga.
