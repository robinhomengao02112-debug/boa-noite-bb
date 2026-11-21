# Pesquisa aprofundada — Notebook com Intel HD Graphics 620

> Documento-base do **Projeto de Otimização**. Ele explica o que é o seu hardware, onde estão os
> gargalos de verdade, o que dá (e o que **não** dá) para melhorar com segurança e como medir os
> resultados. O aplicativo `Otimizador.ps1` aplica exatamente o que está descrito aqui, sempre de
> forma reversível.

---

## 1. Resumo executivo — o que mais dá resultado (em ordem de impacto)

| # | Ação | Ganho típico em jogos | Custo | Quem faz |
|---|------|----------------------|-------|----------|
| 1 | **RAM em dual-channel** (2 pentes iguais) | **o maior de todos** para a HD 620 — a GPU não tem memória própria e depende 100% da largura de banda da RAM | um pente DDR4 SO-DIMM | você / assistência |
| 2 | **Jogar na tomada** + plano de energia de desempenho máximo | evita que CPU e GPU fiquem presas em clocks baixos | zero | **app** |
| 3 | **Driver gráfico Intel atualizado** (série legada 31.0.101.21xx) | correções de desempenho e compatibilidade com jogos | zero | você (link no app) |
| 4 | **Resolução 720p/768p + configurações baixas/médias** nos jogos | dobra ou triplica o FPS frente ao 1080p | zero | você (guia no app) |
| 5 | **SSD** no lugar de HD mecânico | carregamentos e travadinhas de streaming de texturas somem | um SSD SATA | você / assistência |
| 6 | **Limpeza interna + pasta térmica** (notebook de 2017–2019) | elimina *thermal throttling* (queda de clock por calor) | baixo | assistência |
| 7 | Ajustes do Windows (Modo de Jogo, sem gravação em segundo plano, MMCSS, transparência, animações) | alguns % e principalmente **menos travadinhas** | zero | **app** |
| 8 | Limite de FPS estável (30/40/60) com RTSS | sensação de fluidez muito maior com o mesmo FPS médio | zero | você (guia no app) |
| 9 | Upscaling (Magpie/FSR) para rodar abaixo da resolução nativa com imagem melhor | permite resolução interna menor sem ficar "borrado" | zero / baixo | você |

O resto (limpadores de registro, "turbinadores" milagrosos, desativar segurança) **não traz ganho real
ou cria riscos** — ver seção 8.

---

## 2. Identificando o seu notebook

A Intel HD Graphics 620 vem integrada aos processadores **Intel Core de 7ª geração, série U
(Kaby Lake, 15 W)**. A "irmã" **UHD Graphics 620** (8ª geração U, Kaby Lake-R, ex.: i5-8250U) usa a
mesma arquitetura e as mesmas 24 unidades — tudo deste documento vale para as duas.

| Processador | Núcleos/Threads | Clock base / turbo | GPU (clock máx.) |
|-------------|-----------------|--------------------|------------------|
| Core i3-7100U | 2 / 4 | 2,4 GHz / **sem turbo** | HD 620 (até 1000 MHz) |
| Core i3-7130U | 2 / 4 | 2,7 GHz / sem turbo | HD 620 (até 1000 MHz) |
| Core i5-7200U | 2 / 4 | 2,5 / 3,1 GHz | HD 620 (até 1000 MHz) |
| Core i5-7300U | 2 / 4 | 2,6 / 3,5 GHz | HD 620 (até 1100 MHz) |
| Core i7-7500U | 2 / 4 | 2,7 / 3,5 GHz | HD 620 (até 1050 MHz) |
| Core i7-7600U | 2 / 4 | 2,8 / 3,9 GHz | HD 620 (até 1150 MHz) |
| Core i5-8250U / i7-8550U | 4 / 8 | 1,6–1,8 / 3,4–4,0 GHz | **UHD** 620 (até 1100–1150 MHz) |

O aplicativo detecta automaticamente o modelo exato do notebook, processador, cada pente de memória
(tamanho, tipo, velocidade e slot), o disco, a versão do driver e se há GPU dedicada (alguns
notebooks têm HD 620 **e** uma GeForce MX — nesse caso os jogos devem usar a dedicada).

---

## 3. Intel HD Graphics 620 a fundo

### 3.1 Especificações

| Item | Valor |
|------|-------|
| Arquitetura | Intel **Gen9.5** (Kaby Lake), configuração **GT2** |
| Unidades de execução | **24 EUs** = 192 ALUs (shaders) |
| Clock | 300 MHz base; 1000–1150 MHz máximo conforme o processador |
| Desempenho teórico FP32 | ≈ 384–442 GFLOPS (24 EU × 16 FLOP/clock × clock) |
| Memória de vídeo | **não tem própria** — usa a RAM do sistema (memória compartilhada; o Windows pode reservar até metade da RAM) |
| APIs | DirectX 12 (feature level 12_1), OpenGL 4.6, **Vulkan 1.3** (driver 31.0.101.2115 em diante), OpenCL 3.0 |
| Mídia (Quick Sync) | decodificação/codificação H.264, HEVC 10-bit e VP9 em hardware |
| Não suporta | XeSS (precisa de DP4a, que só chegou na Gen12), Agendamento de GPU acelerado por hardware (HAGS), Ray Tracing |

### 3.2 Onde está o gargalo

1. **Largura de banda de memória.** A GPU divide a RAM com o processador. Em **single-channel**
   (um pente só) a banda cai pela metade:

   | Configuração | Banda teórica |
   |--------------|---------------|
   | DDR4-2133, 1 canal (1 pente) | **17,0 GB/s** |
   | DDR4-2133, 2 canais (2 pentes) | **34,1 GB/s** |
   | LPDDR3-1866, 2 canais (soldada) | 29,9 GB/s |

   Testes em iGPUs e GPUs de entrada mostram ganhos tipicamente de 10 a 25 % (às vezes mais) só por
   ativar o dual-channel, e uma análise da própria HD 620 avisa que notebooks com ela em
   single-channel ficam "visivelmente" abaixo dos resultados em dual-channel.
2. **Limite de energia/temperatura (15 W).** CPU e GPU dividem os mesmos 15 W. Se o notebook
   esquenta (poeira, pasta térmica seca), o clock cai — o famoso *thermal throttling*.
3. **Plano de energia/bateria.** Na bateria, muitos fabricantes limitam CPU e GPU. Para jogar,
   **sempre na tomada**.
4. **Driver.** A Intel colocou as gerações 6 a 10 em **suporte legado** (só correções de segurança e
   bugs). A página da HD 620 lista o driver **31.0.101.2115** (dez/2022) e o pacote legado
   "7th–10th Gen" recebeu builds posteriores (31.0.101.214x). Qualquer driver da série
   **31.0.101.21xx** é o recomendado; versões 26.x/27.x/30.x são antigas.

### 3.3 Driver — como atualizar com segurança

1. O app cria um **ponto de restauração** (aba Otimizações → "Criar ponto de restauração").
2. Baixe o pacote oficial **Intel 7th–10th Gen Processor Graphics – Windows** (link no app) ou use o
   **Intel Driver & Support Assistant**.
3. Observação da própria Intel: o driver genérico **substitui o driver personalizado do fabricante
   (OEM)**. Na prática isso é seguro e comum; se algo da tela ficar estranho (ex.: ajuste de brilho
   específico do fabricante), basta voltar pelo ponto de restauração ou reinstalar o driver do site
   do fabricante.
4. Depois, instale o **Intel Graphics Command Center** (Microsoft Store) para os ajustes gráficos.

### 3.4 Ajustes no painel da Intel (Graphics Command Center)

- **Energia → Plano de energia gráfico: "Desempenho máximo"** (na tomada). O plano de energia criado
  pelo app já ajusta isso automaticamente quando o driver expõe a opção.
- **Tecnologia de economia de energia da tela (Display Power Saving): desligada** — não dá FPS, mas
  evita variação de brilho/contraste durante o jogo.
- **Configurações globais 3D:** deixe "usar configurações do aplicativo" (V-Sync, anti-aliasing,
  filtragem). Forçar anti-aliasing no painel derruba o FPS.
- **Escala:** "Manter proporção" ou "Escalonar em tela cheia" para jogar em 1280×720 numa tela 1080p.

---

## 4. Memória RAM — o fator nº 1

### 4.1 O que o processador aceita

| Plataforma | Tipos aceitos | Velocidade máxima oficial | Máx. |
|------------|---------------|---------------------------|------|
| 7ª geração U (Kaby Lake) | DDR4, LPDDR3, DDR3L | **DDR4-2133** / LPDDR3-1866 / DDR3L-1600 | 32 GB |
| 8ª geração U (Kaby Lake-R) | DDR4, LPDDR3 | **DDR4-2400** / LPDDR3-2133 | 32 GB |

Pentes mais rápidos (2400/2666/3200) funcionam, só rodam na velocidade máxima da plataforma.

### 4.2 Recomendações

- **Capacidade:** 4 GB é insuficiente (a GPU "come" parte dela). **8 GB é o mínimo** para jogos;
  **16 GB (2×8 GB)** é o ideal para esse notebook.
- **Dual-channel:** dois pentes **do mesmo tamanho** (idealmente mesma velocidade). Com tamanhos
  diferentes (ex.: 4 GB soldado + 8 GB no slot) o Intel *Flex Mode* faz dual-channel só na parte
  "casada" (4+4) e o restante fica em single — melhor que nada, mas não o ideal.
- **Qual pente comprar:** DDR4 **SO-DIMM** (notebook, 260 pinos), 1,2 V. Confira no app a
  quantidade de slots e o que já está instalado, e confirme o modelo do notebook no site do
  fabricante (alguns têm 4 GB soldados + 1 slot).
- **Como confirmar o modo:** o app mostra a contagem de pentes; para confirmação 100 %, o **CPU-Z**
  (aba *Memory* → *Channel #: Dual*). O teste de RAM do app mostra a banda medida antes/depois.

### 4.3 Memória virtual (arquivo de paginação)

Deixe **"gerenciado pelo sistema"**. Desativar o arquivo de paginação — dica comum em "otimizadores" —
causa travamentos e fechamento de jogos em máquinas com 4–8 GB. O app só **avisa** se ele estiver
desativado.

---

## 5. Processador, temperatura e energia

- CPUs U de 15 W com **Intel Speed Shift (HWP)**: o próprio processador escolhe o clock com base na
  "preferência de desempenho energético" (**EPP**). O plano de energia do app coloca o EPP em
  **0 = desempenho máximo** na tomada, **turbo em modo agressivo** e o plano gráfico da Intel em
  desempenho máximo.
- **Temperatura:** TjMax de 100 °C. Se o notebook passa de ~90 °C em jogo e o clock despenca, a
  causa é física: poeira na ventoinha/dissipador e pasta térmica seca (notebooks de 2017–2019
  quase sempre precisam). Uma limpeza com troca de pasta é a "otimização" de maior impacto depois
  da RAM, e uma base com cooler ajuda.
- **Undervolt (ThrottleStop/XTU): não incluído.** Em Kaby Lake as atualizações de microcódigo/BIOS
  pós-*Plundervolt* (CVE-2019-11157) normalmente bloqueiam o ajuste de tensão, e valores errados
  causam tela azul. Como a regra do projeto é **risco zero**, ficou de fora.
- **Overclock:** impossível/indesejado nesses processadores bloqueados.

---

## 6. Armazenamento

- **HD mecânico (HDD)** é o maior causador de "Windows lento", carregamentos longos e travadinhas
  em jogos de mundo aberto. Um **SSD SATA** (≈ 550 MB/s, ~100× mais rápido em acesso aleatório)
  transforma o notebook. Muitos modelos dessa época têm baia 2,5" SATA e alguns um slot M.2.
- Mantenha **pelo menos 15–20 % livre** no disco do Windows.
- Verifique a saúde do disco com o **CrystalDiskInfo** (o app também lê o status de saúde do Windows).

---

## 7. O que o aplicativo faz no Windows (tudo reversível)

Antes da primeira alteração o app **cria um ponto de restauração** e **salva o valor original de
cada configuração** em `Dados\Backups\estado.json`. Qualquer item pode ser desfeito individualmente
ou todos de uma vez.

| Otimização | O que muda | Por que ajuda | Risco |
|------------|-----------|---------------|-------|
| Plano de energia "Otimizador – Desempenho Máximo" | **Cria um plano novo** (cópia do Alto desempenho) com EPP 0, turbo agressivo, plano gráfico Intel no máximo, sem economia em PCIe/USB/Wi-Fi/disco na tomada. Não altera seus planos existentes. | CPU/GPU respondem rápido e mantêm clock alto | Nenhum (mais consumo na bateria). Desfazer volta ao plano anterior e apaga o novo. |
| Modo de Jogo do Windows | `AllowAutoGameMode`/`AutoGameModeEnabled = 1` | Prioriza o jogo e segura atualizações em segundo plano durante a partida | Nenhum |
| Desativar gravação em segundo plano (Game DVR) | `GameDVR_Enabled`, `AppCaptureEnabled`, `HistoricalCaptureEnabled = 0` | A gravação contínua consome GPU e disco | Nenhum (a Xbox Game Bar continua abrindo) |
| Prioridade de jogos no agendador multimídia (MMCSS) | `SystemResponsiveness = 10`; tarefa *Games*: `Priority = 6`, `Scheduling Category = High`, `SFIO Priority = High` | Mais tempo de CPU e E/S para threads de jogos registradas no MMCSS | Nenhum (valores documentados do MMCSS) |
| Desativar transparência | `EnableTransparency = 0` | O efeito acrílico/blur é renderizado pela própria HD 620 | Só visual |
| Reduzir animações (mantendo fontes suaves) | animações de janela/barra de tarefas/Aero Peek; menus mais rápidos | Menos trabalho do compositor (DWM) e sensação de sistema mais ágil | Só visual |
| Programas de inicialização sem atraso | `StartupDelayInMSec = 0` | Elimina o atraso artificial de ~10 s após o login | Nenhum |
| Desativar dicas, sugestões e apps promovidos | `ContentDeliveryManager` | Menos processos/instalações silenciosas em segundo plano | Nenhum |
| Desativar compartilhamento P2P de atualizações | política `DODownloadMode = 0` | O Windows para de enviar atualizações para outros PCs (CPU, disco e internet) | Nenhum — o Windows Update continua funcionando |
| Otimizações para jogos em janela (Windows 11) | `SwapEffectUpgradeEnable=1` | Modelo de apresentação moderno: menos latência em jogos DX10/11 em janela/sem borda | Nenhum (opção oficial em Configurações → Tela → Gráficos) |
| (Opcional) Desativar apps em segundo plano | `GlobalUserDisabled = 1` | Apps da Store param de rodar escondidos | Apps da Store podem deixar de mostrar notificações |

**Ferramentas extras do app (sob demanda):**

- **Turbo no jogo aberto:** coloca o processo do jogo em prioridade **Alta** (nunca "Tempo real") e
  libera a memória em espera.
- **Prioridade alta permanente:** usa a chave oficial `Image File Execution Options\<jogo>.exe\PerfOptions\CpuPriorityClass = 3`
  — o Windows passa a abrir aquele jogo sempre em prioridade alta. Removível a qualquer momento.
- **Liberar memória em espera:** mesmo comando usado pelo RAMMap (Sysinternals/Microsoft). Útil
  antes de abrir um jogo em máquinas com 4–8 GB.
- **Limpar temporários:** apaga apenas arquivos com mais de 24 h nas pastas TEMP do usuário e do
  Windows, sem seguir atalhos/junções e ignorando arquivos em uso.
- **Testes (benchmark):** CPU 1 núcleo, CPU todos os núcleos e **banda de memória**, com histórico
  "Antes × Depois", e registro manual do FPS dos seus jogos.

---

## 8. O que foi **deliberadamente deixado de fora** (e por quê)

| "Dica" comum | Por que não |
|--------------|-------------|
| Desativar Windows Defender, firewall ou atualizações | Ganho mínimo, risco alto de segurança |
| Desativar mitigações Spectre/Meltdown | Ganho pequeno em jogos, abre falhas de segurança |
| Desativar arquivo de paginação | Causa crashes com pouca RAM |
| Limpadores de registro / "RAM boosters" permanentes | Não aumentam FPS; podem quebrar programas |
| `bcdedit` (HPET, *useplatformtick*, *disabledynamictick*) | Efeito inconsistente ou negativo; é mito na maioria dos casos |
| `NetworkThrottlingIndex`, `Win32PrioritySeparation` | Benefício não comprovado; pode piorar áudio/rede |
| Desativar serviços em massa | Quebra Wi-Fi, impressora, Store, atualizações |
| Apagar cache de shaders | Gera travadinhas na próxima vez que o jogo roda |
| Undervolt/overclock/modificação de BIOS/DVMT | Risco de instabilidade ou de danificar o equipamento |
| Desativar Integridade de Memória (VBS/HVCI) | Pode dar alguns % em jogos, mas reduz a segurança. O app **só informa**; a decisão é sua (Segurança do Windows → Isolamento de núcleo). |

---

## 9. Configurando os jogos para a HD 620

1. **Resolução:** 1280×720 ou 1366×768 em **tela cheia**. É a mudança que mais dá FPS.
2. **Escala de renderização** (quando o jogo tem): 70–85 % com FSR ligado, se disponível.
3. **Desligue/baixe:** sombras, oclusão de ambiente (SSAO/HBAO), reflexos, volumétricos, desfoque de
   movimento, profundidade de campo, anti-aliasing pesado (MSAA/TAA → use FXAA ou desligado).
4. **Texturas:** médio é aceitável com 8 GB em dual-channel; baixo com 4 GB ou single-channel.
5. **API:** em Gen9 o **DirectX 11** costuma ser o mais estável/rápido; teste DX12/Vulkan se o jogo
   oferecer. Para jogos antigos em DX9 com problemas, o **DXVK** (DX9/10/11 → Vulkan) pode ajudar,
   mas é experimental — teste jogo a jogo.
6. **Limite de FPS:** trave em 30, 40 ou 60 (o que o jogo sustentar sempre) com o **RTSS**. Um
   FPS estável parece muito mais fluido que um FPS oscilando. Desligue o V-Sync se usar o limitador.
7. **Upscaling:** jogos em janela/sem borda podem rodar em resolução menor e ser ampliados com
   **Magpie** (gratuito, FSR/escala inteira) ou **Lossless Scaling** (pago, Steam). Na HD 620 use
   só o *upscaling* — a geração de quadros (LSFG) é pesada demais para essa GPU.
8. **Minecraft (Java):** Fabric + **Sodium** (+ Lithium) multiplica o FPS.

### Expectativa realista (estimativas, dual-channel, na tomada, 720p/768p baixo)

| Categoria | Exemplos | Expectativa |
|-----------|----------|-------------|
| Leves / competitivos antigos | League of Legends, Valorant, CS:GO (legado), Dota 2, Roblox, Minecraft + Sodium, indies 2D | **jogáveis**, frequentemente 40–60+ FPS |
| Intermediários | GTA V, Fortnite (modo Desempenho), Rocket League, Skyrim, The Sims 4 | **30 FPS** com ajustes (ex.: GTA V ~27 FPS em 1366×768 e ~32 FPS em 800×600 num teste com HD 620) |
| Pesados | CS2, Apex, Warzone, jogos AAA de 2020+ | no limite ou **injogáveis**, mesmo otimizado |

Em single-channel e/ou com superaquecimento, espere números visivelmente menores — por isso RAM e
limpeza estão no topo da lista.

---

## 10. Metodologia de testes (para comparar antes × depois)

1. Carregador ligado, notebook frio (5 min ligado em repouso), sem programas abertos.
2. No app, aba **Testes**: rótulo "Antes" → rodar teste de CPU + RAM.
3. Em 1–3 jogos que você joga, meça o FPS **sempre no mesmo trecho** por 60 s com CapFrameX ou
   RTSS (FPS médio e *1% low*) e registre no app.
4. Aplique as otimizações, reinicie, repita com rótulo "Depois".
5. Depois de um upgrade de hardware (ex.: 2º pente), rode de novo com rótulo "Dual-channel".
6. "Ver histórico e comparação" mostra a diferença em %.

---

## 11. Ferramentas recomendadas (todas gratuitas, exceto onde indicado)

| Ferramenta | Para quê |
|-----------|----------|
| Intel Graphics Driver 7th–10th Gen / Intel Driver & Support Assistant | driver gráfico atualizado |
| Intel Graphics Command Center | ajustes da GPU |
| CPU-Z | confirmar dual-channel e velocidade da RAM |
| HWiNFO | temperaturas, clocks e *throttling* |
| CrystalDiskInfo | saúde do disco |
| RTSS (RivaTuner Statistics Server) | contador e limitador de FPS |
| CapFrameX | benchmark de FPS / frametime |
| Magpie | upscaling FSR para jogos em janela |
| Lossless Scaling (pago) | upscaling em qualquer jogo |
| Sodium (Minecraft) | otimização do Minecraft Java |
| Unigine Heaven | teste de GPU padronizado |

---

## 12. O seu notebook: Lenovo IdeaPad 330, sem placa dedicada, 20 GB de RAM

O IdeaPad 330 com HD 620 é o **330-15IKB** (ou 330-14IKB), com Core i3-7020U/7130U, i5-7200U ou
i7-7500U (se for i5-8250U/i7-8550U, a GPU é a UHD 620 — mesma arquitetura, tudo abaixo vale igual).
O app identifica o modelo exato automaticamente.

### 12.1 Memória: por que você tem 20 GB e o que isso significa

- A placa do 330-15IKB tem **4 GB DDR4 soldados** + **um único slot SO-DIMM**. Os seus 20 GB são
  **4 GB soldados + um pente de 16 GB**.
- Com pentes de tamanhos diferentes, o processador usa o **Intel Flex Mode**: **8 GB (4 + 4)
  funcionam em dual-channel** e os **12 GB restantes em single-channel**.
- **Recomendação: mantenha os 20 GB.** A única forma de ter dual-channel completo seria trocar o pente
  de 16 GB por um de 4 GB (total 8 GB) — você ganharia um pouco de banda, mas perderia 12 GB de
  capacidade, o que causa mais travadas com navegador/Discord abertos. Não compensa.
- Com 20 GB, a HD 620 pode usar até cerca de **10 GB como memória de vídeo** (o Windows libera até
  metade da RAM): **texturas no médio** são seguras na maioria dos jogos.
- A memória roda a **2133 MHz** (limite da plataforma), mesmo que o pente seja de 2400/2666 — normal.
- Observação: a ficha técnica da Lenovo cita **16 GB como máximo oficial**; muitos 330 reconhecem os
  20 GB normalmente. O app compara RAM instalada × utilizável e avisa se o Windows não estiver usando
  tudo.

### 12.2 Armazenamento

- A maioria dos 330-15IKB veio com **HD mecânico de 1 TB (5400 rpm)** — o maior causador de lentidão
  nesse modelo.
- A ficha técnica da Lenovo lista um **slot M.2 2280 PCIe NVMe** além da baia SATA de 2,5". O melhor
  upgrade é um **SSD NVMe M.2 2280** para o Windows e os jogos, mantendo o HD para arquivos. O app
  detecta se o Windows está em HDD ou SSD.

### 12.3 Refrigeração e energia

- **Uma única ventoinha** e dissipador simples: o modelo esquenta com facilidade. Superfície firme,
  saídas de ar livres e base com cooler ajudam; limpeza + pasta térmica se passar de ~90 °C em jogo.
- **Lenovo Vantage** (Microsoft Store): atualizações de BIOS e drivers da Lenovo (sempre com
  carregador ligado). Se a sua versão mostrar **Modo térmico/Desempenho**, escolha *Desempenho* para
  jogar. O **Modo de conservação** da bateria preserva a bateria quando o notebook vive na tomada,
  sem reduzir desempenho.
- **Na tomada sempre** para jogar: na bateria o 330 limita a CPU e a GPU.

### 12.4 Tela

- Tela **HD 1366×768**: jogue na resolução **nativa** (1366×768) ou em 1280×720 se precisar de mais FPS.
- Tela **Full HD**: use 1280×720 em tela cheia.

---

## 13. Fontes

- Intel — página de suporte e drivers da HD Graphics 620: <https://intel.com/content/www/us/en/support/products/96551.html>
- Intel — Intel 7th–10th Gen Processor Graphics – Windows: <https://www.intel.com/content/www/us/en/download/776137/intel-7th-10th-gen-processor-graphics-windows.html>
- Intel — notas de versão 31.0.101.2115 (Vulkan 1.3, suporte legado): <https://downloadmirror.intel.com/764512/ReleaseNotes_101.2115_WHQL.pdf>
- Neowin — driver legado 31.0.101.2121 para 6ª–10ª geração: <https://www.neowin.net/news/intel-graphics-driver-3101012121-whql-for-6th-10th-gen-intel-cpus-released/>
- WikiChip — HD Graphics 620 (Gen9.5 GT2, 24 EUs, clocks por CPU): <https://en.wikichip.org/wiki/intel/hd_graphics_620>
- TechPowerUp — especificações HD Graphics 620: <https://techpowerup.com/gpu-specs/hd-graphics-620.c2911>
- Notebookcheck — comparativos e classificação de desempenho da HD 620: <https://www.notebookcheck.net/HD-Graphics-620-vs-UHD-Graphics-620_7435_8098.247598.0.html>
- LaptopMedia — jogos na HD 620 (CS:GO, GTA V): <https://laptopmedia.com/highlights/gaming-on-intels-hd-graphics-620-kaby-lake-sure-why-not/>
- Laptoping — análise da HD 620 (aviso sobre single-channel): <https://laptoping.com/gpus/product/intel-hd-620-review-graphics-of-7th-gen-core-u-series-kaby-lake-cpus/>
- Matt Gadient — upgrade de RAM na UHD 620: <https://mattgadient.com/faster-gaming-on-intel-uhd-620-kaby-lake-r-by-upgrading-ram>
- LaptopMedia — single vs. dual-channel em jogos: <https://laptopmedia.com/highlights/the-bottleneck-effect-gaming-performance-with-single-vs-dual-channel-memory/>
- UltrabookReview — single vs. dual-channel (10–25 % em vários jogos): <https://www.ultrabookreview.com/36308-single-dual-channel-ram-amd>
- Lenovo PSREF — especificação do IdeaPad 330-15IKB: <https://psref.lenovo.com/syspool/Sys/PDF/IdeaPad/ideapad_330_15IKB/ideapad_330_15IKB_Spec.PDF>
- Kingston — memória compatível com o IdeaPad 330-15IKB: <https://www.kingston.com/en/memory/search/model/102200/lenovo-ideapad-330-15ikb>
- Crucial — upgrades compatíveis com o IdeaPad 330-15IKB: <https://crucial.com/compatible-upgrade-for/lenovo/ideapad-330-15ikb>
