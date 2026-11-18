# Buds Control

App Android para controlar os **Redmi Buds 6 Lite** (e outros fones Xiaomi/Redmi que usam o mesmo protocolo).
Ele fala direto com o chip do fone pelo canal de controle Bluetooth da Xiaomi (RFCOMM `0xFD2D`), sem nuvem e sem conta.

## Recursos
- **Bateria** do fone esquerdo, do direito e do estojo, com indicação de carregamento; detecção de uso (no ouvido ou no estojo).
- **Modos de som:** cancelamento de ruído, transparência ou desligado.
- **Intensidade do ANC:** leve, equilibrado ou profundo; ANC adaptativo do próprio fone.
- **Turbo ANC:** isolamento máximo que o hardware permite, com um toque.
- **ANC Inteligente:** mede o ruído ambiente pelo microfone do celular e troca a intensidade sozinho, com histerese e limites configuráveis.
- **Equalizador no fone:** presets e EQ personalizado de 10 bandas (−6 a +6 dB), com curvas prontas.
- **Gestos:** toque simples, duplo, triplo e longo, configuráveis para cada lado; ciclo do toque longo.
- **Detecção de uso**, atendimento automático de chamadas e conexão dupla.
- **Estabilidade:** reconexão automática, histórico de quedas, atalhos para as configurações de codec e bateria, e dicas de estabilidade.
- **Perfis** (Ônibus, Academia, Dormir…).
- **Notificação** com botões rápidos de modo de som.
- **Console do protocolo e log**, para testar comandos não mapeados.

## Instalação
1. Abra a aba **Releases** do repositório no celular e baixe o `BudsControl.apk` da versão mais recente.
2. Instale o arquivo, permitindo a instalação de apps desconhecidos.
3. Pareie os fones normalmente no Bluetooth do Android, abra o app e escolha os fones.
4. No Xiaomi/Redmi (HyperOS/MIUI), ative **Início automático** e tire a restrição de bateria do app.

> Feche o app oficial Xiaomi Earbuds enquanto usa este: os dois disputam o mesmo canal de controle.

## Limites honestos
O cancelamento de ruído é processado pelo chip do fone (até cerca de 40 dB no Buds 6 Lite). O app escolhe e ajusta os modos
que o firmware oferece, mas não consegue passar desse limite físico. Recursos que não existem no firmware
do seu modelo são simplesmente ignorados pelo fone.

## Créditos e licença
O protocolo e a autenticação foram portados do projeto [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge)
(suporte aos Redmi Buds 5 Pro), licenciado sob **GNU AGPL v3**. Este app segue a mesma licença.
