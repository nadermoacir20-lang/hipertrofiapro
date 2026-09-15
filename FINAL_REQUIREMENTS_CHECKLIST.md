# Hipertrofia Pro v7 — checklist final Android

## Treino
- [x] Fluxo principal peso → reps → ✓ → descanso.
- [x] Aquecimento tratado como set leve obrigatório quando planeado.
- [x] Peso e reps reais introduzidos manualmente; alvo apenas referência.
- [x] Cargas por lado e dois halteres exibidas como `x+x kg`.
- [x] Unilateral: uma carga, reps E/D separadas, exigência de reps iguais.
- [x] Peso corporal sem campo de kg.
- [x] TBD/reps livres suportados.
- [x] Observação geral + observação por série escondida em 💬.
- [x] Série concluída permanece editável; desmarcar ✓ preserva os dados.
- [x] Número de séries bloqueado durante treino.
- [x] Fazer depois / Saltar / Concluído separados.
- [x] Ordem oficial preservada; execução fora de ordem não reescreve o plano.
- [x] Terminar treino é explícito.
- [x] Treino iniciado por engano pode ser removido; treino real pode ser encerrado como incompleto.

## Descanso
- [x] Descanso por exercício/set.
- [x] ✓ inicia descanso.
- [x] Continua a contar acima do alvo.
- [x] Pausa e Terminar descanso; sem ±30 s.
- [x] Tempo baseado em timestamps e recuperável após process death.
- [x] Som + vibração no fim; sons podem ser desligados, vibração permanece.
- [x] Sem notificação de sistema para fim do descanso.
- [x] Próxima série/exercício destacado.
- [x] Relatório guarda descanso real e desconta tempo em pausa.

## Persistência / segurança de dados
- [x] `localStorage` imediato.
- [x] snapshots locais rotativos.
- [x] mirror IndexedDB.
- [x] mirror nativo em `AtomicFile` + fallback SharedPreferences.
- [x] recuperação escolhe snapshot nativo mais recente quando necessário.
- [x] Android Auto Backup + device transfer para estado nativo.
- [x] backup/import manual JSON nativo.
- [x] plano tem backups/undo independentes do histórico de sessões.
- [x] datas locais, não UTC.

## Android APK
- [x] API/target 36.
- [x] Edge-to-edge Android 16 com insets de barras/teclado tratados nativamente.
- [x] Java 17.
- [x] `WebViewAssetLoader`, sem `file://`.
- [x] URLs externas abrem fora do WebView.
- [x] `FLAG_KEEP_SCREEN_ON` apenas durante treino activo.
- [x] baixo custo gráfico para Samsung A07.
- [x] hardware back fecha modal/volta a Treino antes de sair.
- [x] lembrete semanal de peso segunda de manhã; fallback interno.
- [x] vibração nativa.
- [x] workflow GitHub Actions preservando a estrutura do projecto.

## Histórico / progresso
- [x] calendário: concluído / incompleto / com saltos / ausência planeada.
- [x] várias sessões no mesmo dia.
- [x] edição protegida e eliminação confirmada de histórico.
- [x] volume exclui warmups.
- [x] PRs excluem warmups.
- [x] gráficos por exercício + peso corporal.
- [x] estagnação e queda de desempenho apenas informativas.
- [x] ausências planeadas, inclusive intervalo de vários dias.

## ChatGPT / plano
- [x] relatório com alvo + execução + notas + descansos + últimas 3 sessões.
- [x] alterações manuais não reconhecidas por patch são reportadas.
- [x] app nunca decide progressão automaticamente.
- [x] patch manual colado → validar → preview → confirmar → undo.
- [x] IDs estáveis e histórico não apagado quando o plano muda.

## Interface
- [x] dark permanente.
- [x] roxo/índigo/azul.
- [x] estética gym/fitness forte.
- [x] cards expansíveis com transições suaves estilo iOS.
- [x] um exercício expandido de cada vez.
- [x] barra inferior Treino / Histórico / Progresso / Mais.
- [x] dias Seg–Sex no topo.
- [x] progresso de treino + duração discretos.
- [x] kg apenas; português apenas.
- [x] sem RIR.

## Externo / pendente por credenciais
- [ ] Google Sign-In real obrigatório no primeiro acesso.
- [ ] Cloud sync Google/Firebase Android ↔ futuro iPhone.

Esses dois itens exigem credenciais externas válidas do projecto Google/Firebase; não são simulados.
