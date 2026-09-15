# Hipertrofia Pro v7 — Android

Projecto Android da Hipertrofia Pro v7, baseado na interface v7 aprovada e preparado para compilar um APK offline-first.

## Estado desta versão

A parte local/Android está integrada: treino, histórico, progresso, patches do ChatGPT, autosave, redundância de dados, backup/importação nativos, edge-to-edge, teclado/insets, vibração nativa, ecrã ligado apenas durante treino activo, hardware back e lembrete semanal de peso.

Continuam deliberadamente pendentes apenas os dois requisitos que precisam de credenciais externas válidas:

- Google Sign-In real obrigatório no primeiro acesso;
- sincronização cloud Google/Firebase Android ↔ futuro iPhone.

A aplicação não simula esses dois pontos. Até as credenciais serem configuradas, funciona em modo local e offline.

## Segurança dos dados

O estado é guardado em várias camadas:

1. `localStorage` imediato no WebView;
2. snapshots locais rotativos;
3. mirror em IndexedDB;
4. mirror nativo em `AtomicFile`;
5. fallback nativo em `SharedPreferences`;
6. Android Auto Backup/device transfer para o estado nativo;
7. backup/importação manual em JSON pelo Storage Access Framework.

No arranque, a app compara as cópias síncronas disponíveis e usa a v7 válida mais recente. O mirror IndexedDB também pode recuperar um estado mais recente depois de a interface arrancar.

## Descanso

O descanso é baseado em timestamps persistidos, continua acima do alvo e recupera depois de fechar/reabrir ou de o processo ser morto. Pausas são descontadas do descanso real mostrado no relatório. O fim do descanso usa som (se activado) e vibração; não cria notificação de sistema.

## Android

- `applicationId`: `mz.nader.hipertrofiapro`
- `minSdk`: 24
- `targetSdk` / `compileSdk`: 36
- Java 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13 no GitHub Actions
- AndroidX WebKit 1.16.0
- `WebViewAssetLoader` em HTTPS interno, sem `file://`
- cleartext desactivado
- edge-to-edge e insets de barras/IME tratados nativamente

## Compilar com GitHub Actions

O workflow `.github/workflows/build-apk.yml` compila automaticamente em pushes para `main` e também pode ser executado manualmente.

Artifact gerado:

`Hipertrofia-Pro-v7-APK` → `Hipertrofia-Pro-v7.apk`

A chave de debug é mantida em cache pelo workflow para permitir actualizar instalações debug anteriores produzidas pelo mesmo repositório.

## Compilar no Android Studio

1. Abre a raiz deste projecto no Android Studio.
2. Instala o Android SDK 36 quando solicitado.
3. Sincroniza o Gradle.
4. Executa `assembleDebug` ou usa **Build APK(s)**.
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Estrutura principal

- `app/src/main/assets/www/index.html` — interface e lógica v7.
- `MainActivity.java` — WebView segura, bridge Android, backup/importação, edge-to-edge, clipboard, partilha e hardware back.
- `NativeStateStore.java` — persistência nativa redundante com `AtomicFile` + `SharedPreferences`.
- `WeightReminderScheduler.java` — agenda o lembrete semanal de peso.
- `WeightReminderReceiver.java` — mostra o lembrete quando necessário.
- `BootReceiver.java` — restaura o agendamento após reboot/actualização.
- `.github/workflows/build-apk.yml` — geração do APK.
- `FINAL_REQUIREMENTS_CHECKLIST.md` — checklist funcional da v7.

## Actualizações do plano por ChatGPT

O formato continua documentado em `PATCH_FORMAT.md`. A aplicação valida, pré-visualiza e pede confirmação antes de aplicar um patch; o plano anterior pode ser restaurado sem apagar o histórico de treinos.
