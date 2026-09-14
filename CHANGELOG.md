# Changelog

## v6.0.0 — 14/09/2026

### Robustez
- Estado redundante: WebView localStorage + snapshot nativo em SharedPreferences.
- Auto-save ao digitar e ao app ir para segundo plano.
- Data local do dispositivo, não UTC.
- Novo dia abre automaticamente o dia da semana correcto.
- Backups nativos via Android Storage Access Framework.
- Android Auto Backup/device transfer inclui o snapshot nativo.

### Treino
- Temporizador nativo de descanso com serviço foreground curto, notificação e vibração.
- Ecrã pode permanecer ligado durante o treino.
- RIR/margem estimada opcional.
- Motivo de parar: alvo, segurança, falha, técnica, dor/cãibra, etc.
- Sono e estado muscular no contexto da sessão.
- Último treino do mesmo exercício visível no cartão.
- Pesquisa externa rápida de técnica no YouTube.

### Integração AI
- Relatório mais rico para análise.
- Patches JSON para alterar pesos, séries, reps, técnica, notas, exercícios e ordem sem editar código.
- Backup automático antes de cada patch e botão para desfazer o último.
- Partilha nativa do relatório no Android.

### Android
- Target/compile SDK 36.
- AndroidX WebKit 1.16.0.
- WebViewAssetLoader em vez de file://.
- Sem cleartext traffic e sem acesso WebView arbitrário a ficheiros/conteúdo.
- Workflow GitHub Actions para gerar APK.
