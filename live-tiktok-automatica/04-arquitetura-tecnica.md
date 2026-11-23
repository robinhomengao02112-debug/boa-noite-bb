# 04 — Arquitetura técnica no seu PC

## 4.1 Visão geral
```
                ┌──────────────────────────── SEU PC (Windows) ─────────────────────────────┐
 Espectadores   │                                                                          │
 (app TikTok)   │  [Servidor do jogo – Node.js]  ◄── eventos (presentes, comentários,      │
     │          │     • regras do jogo               curtidas, seguidores)                 │
     │          │     • placar / registro receita        ▲                                 │
     │          │     • /health  /api/stats              │ tiktok-live-connector           │
     │          │            │ socket.io                  │ (assinatura via Euler Stream)  │
     │          │            ▼                            │                                 │
     │          │  [Overlay do jogo – página web 1080x1920]                                │
     │          │            │ Fonte de navegador                                          │
     │          │            ▼                                                             │
     │          │  [OBS Studio  ou  TikTok LIVE Studio] ◄── webcam / VDO.Ninja (apresentador)│
     │          │            │ alertas TikFinity (opcional)                                │
     │          │            ▼                                                             │
     └──────────┼──── TikTok LIVE (RTMP com chave, ou envio direto pelo LIVE Studio) ◄─────┘
                │  [Supervisor]: reinicia o servidor se cair ou travar                     │
                │  [Agendador de Tarefas]: liga e desliga nos horários                     │
                └──────────────────────────────────────────────────────────────────────────┘
```

## 4.2 Peças e por que cada uma

| Peça | Escolha | Motivo |
|---|---|---|
| Leitura dos eventos da live | **`tiktok-live-connector` v2.5.0** (Node.js, publicado em 16/09/2026) | É a biblioteca mais usada e mantida. Expõe `gift`, `chat`, `like`, `follow`, `share`, `member`, `streamEnd` e mais de 60 outros eventos. Alternativa em Python: **`TikTokLive` 7.0.1** (10/09/2026). |
| Assinatura da conexão | **Euler Stream** (plano gratuito, chave opcional) | As duas bibliotecas usam o servidor de assinatura da Euler. Sem chave valem os limites comunitários; com chave grátis, limites maiores. |
| Motor do jogo | **Node.js + socket.io + página web (Canvas/DOM)** | Roda em qualquer PC, entra no OBS e no LIVE Studio como "fonte de navegador", é fácil de mudar e não precisa de engine pesada. Para 3D/física, dá para trocar por Unity ou Godot mais tarde, usando o mesmo servidor. |
| Transmissão | **TikTok LIVE Studio** (oficial) ou **OBS Studio** com chave RTMP | O LIVE Studio é o caminho oficial e aceita fonte de navegador. O OBS dá mais controle e automação (linha de comando + obs-websocket), mas exige uma **chave de transmissão**, que nem toda conta tem. Um guia de terceiros diz que a chave pode expirar, então **teste**. |
| Alertas sem código (opcional) | **TikFinity** | Alertas, TTS, metas, "Actions & Events", simulação de teclas para controlar jogos do Windows com presentes, integração com Minecraft e Streamer.bot. |
| Apresentador remoto | **VDO.Ninja** | Webcam do apresentador → seu OBS, gratuito e com baixa latência. |
| Piloto automático | **`src/controller.js`** (incluído) | Mantém o jogo e o OBS de pé, configura o OBS sozinho, liga e desliga a transmissão, religa após quedas e tem painel em `http://127.0.0.1:3001`. |

## 4.3 Detalhes importantes da biblioteca (já tratados no protótipo)
- **Presentes em sequência (combo):** presentes com `giftType === 1` chegam várias vezes durante o combo e uma última vez com `repeatEnd`. **Só o último evento deve contar**, senão o mesmo presente é contado em dobro. Está tratado em `starter/src/sources/tiktok.js`.
- **Valor:** `giftDetails.diamondCount` (custo em moedas) × `repeatCount`.
- **Só conecta se a live estiver no ar.** Antes disso dá `UserOfflineError`. O protótipo tenta de novo com espera crescente (5 s → 60 s) para não levar bloqueio por excesso de tentativas.
- O README da biblioteca avisa: "This is not a production-ready API. It is a reverse engineering project." O TikTok pode mudar o protocolo e quebrar a conexão. **Mitigação:** manter a biblioteca atualizada (`npm update`) e ter o TikFinity ou uma API paga (TikTool, Euler WebSocket) como plano B.

## 4.4 Configuração do OBS (pontos de partida; teste com a sua internet)
- **Tela:** vertical, base **1080x1920**. Saída 1080x1920 ou **720x1280** se o upload for fraco.
- **FPS:** 30 (60 só se o jogo for muito rápido).
- **Bitrate:** **CBR** de 3.000–6.000 kbps. Use no máximo cerca de 50–60% do seu upload real.
- **Encoder:** NVENC (placa NVIDIA) ou AMF/QuickSync. Evite x264 no processador num PC que fica 24h ligado.
- **Keyframe:** 2 s.
- **Cena "Jogo":** Fonte de navegador `http://127.0.0.1:3000/` (criada automaticamente pelo controlador) (1080x1920) + webcam/VDO.Ninja num canto + alertas do TikFinity.
- **Cena "Intervalo":** placar do dia + "voltamos já". Use por **poucos minutos**, nunca como tela parada por horas.
- **Replay Buffer** ligado e um atalho para salvar os melhores momentos, que viram cortes para vídeos.

## 4.5 Automação de ligar e desligar (Windows)
> **Já implementado:** o `INSTALAR.bat` faz tudo isto sozinho (ver `COMO-USAR.md`). Os itens abaixo explicam como funciona.

1. **Controlador:** inicia escondido com o Windows (atalho na pasta Inicializar) e sobe o servidor do jogo.
2. **OBS começando a transmitir sozinho:** `obs64.exe --startstreaming --scene "Jogo" --minimize-to-tray` (opções oficiais de linha de comando do OBS). Agende num horário fixo.
3. **Parar a transmissão num horário:** use o **obs-websocket**, que já vem no OBS 28 ou mais novo (Ferramentas → Configurações do WebSocket). Por script: `obs-websocket-js` 5.0.8 com a chamada `StopStream`. Também dá para agendar uma tarefa que fecha o OBS.
4. **Se usar o LIVE Studio em vez do OBS:** ele não tem linha de comando documentada. A automação fica no jogo e nos alertas, e o início e o fim são feitos pelo apresentador ou por você. É uma troca: o LIVE Studio é oficial, mas automatiza menos.

## 4.6 PC e rede para rodar muitas horas
| Item | Mínimo | Recomendado |
|---|---|---|
| Processador | 4 núcleos | 6–8 núcleos |
| Placa de vídeo | Com encoder (NVENC/AMF) | GTX 1650 / RTX 3050 ou melhor |
| RAM | 8 GB | 16 GB |
| Disco | SSD | SSD com 100 GB livres para gravações |
| Internet | Upload 10 Mbps estável | Fibra, upload ≥ 20 Mbps, **cabo de rede** (não Wi-Fi) |
| Energia | — | **Nobreak** |
| Windows | — | Suspensão desligada; "Horário ativo" do Windows Update fora do horário da live; driver de vídeo atualizado |

## 4.7 Monitoramento
- `http://127.0.0.1:3000/health` mostra se está conectado, há quanto tempo está sem eventos, o número de reinícios e o tempo ligado.
- `http://127.0.0.1:3000/api/stats` mostra os presentes do dia, as moedas, a receita estimada em US$ e quantas pessoas diferentes deram presente. `?day=AAAA-MM-DD` mostra outro dia.
- `starter/data/ledger-AAAA-MM-DD.jsonl` tem uma linha por presente, a base para saber **quais horários e quais presentes pagam**.
- `data/controlador.log` registra quedas, reinícios e cada liga/desliga. `data/aprendizado.json` guarda quanto cada jogo rende por minuto.
- **Próxima evolução sugerida:** alerta no celular (Telegram ou e-mail) quando a live cair ou ficar parada.
