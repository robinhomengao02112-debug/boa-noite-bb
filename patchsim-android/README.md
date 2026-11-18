# PatchSim Lab

**Laboratório Android de simulação de assinaturas, compras e licenças, análise de APKs e
gerenciamento de patches reversíveis — 100% local.**

PatchSim Lab é um aplicativo de testes para desenvolvedores, inspirado no *conjunto de
recursos técnicos* de ferramentas como o Lucky Patcher, porém construído para um propósito
legítimo: **testar o próprio app (ou apps autorizados) contra estados de monetização
simulados**, sem tocar em nenhum sistema real de pagamento ou licenciamento de terceiros.

É o equivalente, feito de forma offline e auto-contida, aos *test SKUs* do Google Play e às
*static license responses* usadas no desenvolvimento.

---

## ⚠️ Escopo e limites (leia antes de usar)

- Todos os simuladores operam **localmente e em memória/Room**. Nada é enviado a uma loja,
  gateway de pagamento ou servidor de licença real.
- O **Gerenciador de Patches** transforma apenas um *modelo de entitlement em sandbox* do
  app-alvo; ele **não reescreve, reempacota nem re-assina** nenhum APK de terceiros e **não
  remove proteções** de apps que você não controla. Toda alteração tem backup automático e é
  reversível.
- O **Analisador de APKs** apenas **lê** metadados (permissões, componentes, bibliotecas,
  sinais de billing) via `PackageManager`. Nunca modifica arquivos.
- Use somente em apps **próprios ou com autorização explícita**.

---

## Funcionalidades

| # | Módulo | O que faz |
|---|--------|-----------|
| 1 | **Analisador de APKs** | Lista apps instalados ou analisa um `.apk`: permissões, activities/services/receivers/providers, ABIs nativas e sinais de compras integradas. |
| 2 | **Simulador de Assinaturas** | Reproduz estados `FREE`, `PREMIUM`, `PRO`, ativo, em carência e expirado, com avaliação determinística de expiração. |
| 3 | **Simulador de Compras** | Catálogo de teste com preços fictícios, incluindo **R$ 0,00**; fluxos de compra, pendência, confirmação e reembolso. |
| 4 | **Gerenciador de Patches** | Cria, aplica e **reverte** modificações no estado de entitlement em sandbox, com backup automático antes de cada aplicação. |
| 5 | **Servidor de Licença (mock)** | Servidor HTTP local em *loopback* (`127.0.0.1`) que responde `LICENSED` / `NOT_LICENSED` / `RETRY` / `ERROR` para testar a reação do seu app. |
| 6 | **Painel de Controle** | Seleciona apps de teste, mostra o resumo e administra as simulações. |
| 7 | **Backup e Restauração** | Snapshots do estado de entitlement, com restauração a qualquer momento. |
| + | **Demo Premium App** | App embutido com recursos premium que reagem em tempo real aos simuladores — valida tudo de ponta a ponta. |
| + | **Logs** | Trilha de execução e diagnóstico de todas as operações. |

---

## Arquitetura

- **Linguagem:** Kotlin · **UI:** Jetpack Compose + Material Design 3
- **Padrão:** MVVM (ViewModel + `StateFlow` + Repository)
- **Persistência:** Room
- **DI:** Service Locator manual (sem processadores de anotação além do Room)
- **Modular:** `core/` (lógica pura, sem Android) · `data/` · `feature/<módulo>`

```
app/src/main/java/com/sandbox/patchsim/
├── core/
│   ├── model/        # modelos de domínio (sem Android)
│   └── sim/          # motores puros: Subscription, Billing, Patch, License
├── data/
│   ├── db/           # Room: entidades, DAOs, AppDatabase
│   ├── repo/         # repositórios + ApkAnalyzer + mappers
│   └── server/       # MockLicenseServer (loopback)
├── di/               # ServiceLocator
├── feature/<módulo>/ # ViewModel + Screen de cada módulo
└── ui/               # tema Material 3 + componentes
```

A lógica de negócio vive em `core/sim` como **classes Kotlin puras e determinísticas**
(relógio injetável), o que as torna totalmente testáveis em JVM — veja os testes.

---

## Como compilar e executar

Requisitos: **Android Studio Ladybug+** (ou SDK de linha de comando) e **JDK 17**.

```bash
# via Android Studio: abra a pasta patchsim-android/ e clique em Run.

# via linha de comando (precisa do Android SDK instalado e ANDROID_HOME configurado):
cd patchsim-android
./gradlew assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # instala em um device/emulador conectado
```

> O `gradle-wrapper.jar` já está incluído. A primeira build baixa AGP, Compose e Room do
> Google Maven (`dl.google.com`) e do Maven Central.

---

## Como verificar as simulações (testes)

Os motores de simulação têm testes de unidade JVM (não precisam de device):

```bash
cd patchsim-android
./gradlew test                 # executa todos os testes de unidade
```

Cobertura atual (17 testes):

- `SubscriptionSimulatorTest` — ativo / expirado / carência / entitlement.
- `BillingSimulatorTest` — compra, **override de preço para R$ 0,00**, reembolso, posse.
- `PatchProcessorTest` — aplicar/reverter cada tipo de patch.
- `LicensePolicyTest` — vereditos de licença.

### Roteiro de validação manual (ponta a ponta)

1. Abra o app → aba **Demo Premium**: todos os recursos pagos aparecem **bloqueados**.
2. Vá em **Assinaturas** → no *Demo Premium App*, toque em **Pro ativo**.
3. Volte em **Demo Premium**: AI Assistant e Export PDF ficam **desbloqueados**.
4. Em **Compras**, compre um SKU com o botão **R$ 0,00** → aparece no histórico a R$ 0,00.
5. Em **Patches**, aplique *"Unlock Premium (demo)"* e depois **Reverter** — confira em
   **Backup** o snapshot automático.
6. Em **Licenças**, inicie o servidor e toque em **Testar verificação** — veja o JSON de
   resposta mudar conforme o veredito configurado.
7. Acompanhe tudo na aba **Logs**.

---

## Expansão

Novos módulos seguem o mesmo contrato: um motor puro em `core/sim`, um repositório em
`data/repo`, e um par `ViewModel`/`Screen` em `feature/`, registrado em `VmRegistry`.
