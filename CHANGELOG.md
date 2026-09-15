# Changelog

## v7.0.0 — 15/09/2026

### Interface e treino
- Interface Hipertrofia Pro v7 integrada no APK.
- Fluxo peso → reps → conclusão → descanso.
- Aquecimentos tratados como séries leves quando planeados.
- Cargas por lado/halteres em `x+x kg`, unilateral E/D e peso corporal suportados.
- Fazer depois, saltar e concluir permanecem estados separados.
- Histórico, progresso, editor do plano e patches ChatGPT preservados.

### Descanso
- Timer baseado em timestamps persistidos.
- Recuperação após reabertura/process death.
- Contagem continua acima do alvo.
- Pausa e terminar descanso sem botões ±30 s.
- Pausas descontadas do descanso real no relatório.
- Som opcional e vibração nativa; sem notificação de sistema para o fim do descanso.

### Persistência
- `localStorage` + snapshots rotativos.
- Mirror IndexedDB e recuperação quando contém estado mais recente.
- Mirror nativo em `AtomicFile` com fallback `SharedPreferences`.
- Android Auto Backup/device transfer do estado nativo.
- Backup/importação JSON através do selector nativo de ficheiros.

### Android
- API/target 36 e Java 17.
- Edge-to-edge com insets das barras e IME tratados nativamente.
- `WebViewAssetLoader`, cleartext desactivado e URLs externas fora do WebView.
- `FLAG_KEEP_SCREEN_ON` apenas durante sessão activa.
- Hardware back fecha modal / regressa ao treino antes de sair.
- Lembrete semanal de peso à segunda-feira de manhã, com fallback interno da interface.
- Workflow GitHub Actions gera `Hipertrofia-Pro-v7.apk`.

### Pendente por credenciais externas
- Google Sign-In real obrigatório.
- Sincronização cloud Google/Firebase Android ↔ futuro iPhone.
