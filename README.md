# Hipertrofia Pro v6 — Android

Aplicação pessoal de treino, offline-first, feita para registo rápido no ginásio e integração por copy-paste com ChatGPT.

## O que esta versão resolve

- Auto-save de cada peso, repetição, série, execução e observação.
- Rascunhos recuperados após fechar/reabrir a aplicação.
- Cópia redundante do estado em `SharedPreferences`, além do armazenamento do WebView.
- Temporizador nativo de descanso com notificação e vibração, resistente ao app ir para segundo plano.
- Opção de manter o ecrã ligado durante o treino.
- Histórico por sessão e referência ao treino anterior do mesmo exercício.
- RIR/margem estimada e motivo para terminar a série/exercício.
- Exportação de relatório para ChatGPT e aplicação de patches JSON devolvidos pela AI.
- Backup/importação por ficheiro JSON.
- Exportação/partilha nativa no Android.
- Abertura externa de vídeos de técnica sem arriscar perder o treino.
- Plano actual já inclui as correcções de Legs A discutidas em 14/09/2026.

## Compilar no Android Studio

1. Instala Android Studio recente.
2. Abre esta pasta como projecto.
3. Deixa o Gradle sincronizar e instalar Android SDK 36 caso seja pedido.
4. `Build > Build App Bundle(s) / APK(s) > Build APK(s)`.
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Compilar sem Android Studio usando GitHub Actions

O ficheiro `.github/workflows/build-apk.yml` já está preparado.

1. Cria um repositório privado no GitHub e envia o conteúdo desta pasta.
2. Abre `Actions > Build Android APK > Run workflow`.
3. Quando terminar, descarrega o artifact `Hipertrofia-Pro-v6-APK`.
4. Instala o APK no Android.

O workflow mantém em cache a chave de assinatura de debug para que APKs futuros do mesmo repositório possam actualizar a instalação existente. Mantém o repositório privado se fores guardar nele dados pessoais ou outras personalizações.

## Migrar do HTML/PWA para o APK

O Chrome e o APK não partilham o mesmo armazenamento.

1. Na versão HTML: `Perfil / Backup > Backup JSON`.
2. Instala o APK.
3. No APK: `Perfil / Backup > Importar ficheiro` ou cola o JSON.
4. Restaura o backup.

## Estrutura

- `app/src/main/assets/www/index.html` — interface e lógica do treino.
- `MainActivity.java` — WebView segura, backups, partilha, clipboard, ficheiros e ponte nativa.
- `RestTimerService.java` — temporizador nativo de descanso.
- `.github/workflows/build-apk.yml` — compilação automática do APK.

## Actualizações do plano por ChatGPT

Na aplicação: `AI / Histórico > Aplicar actualização da AI`.

O ChatGPT pode devolver um patch como:

```json
{
  "type": "hipertrofia-pro-patch",
  "patchVersion": 1,
  "label": "Ajustes após treino",
  "changes": [
    {
      "op": "update",
      "day": "Segunda",
      "id": "mon-smith",
      "set": {
        "targetWeight": "15",
        "targetReps": "8–12"
      }
    }
  ]
}
```

Cola e aplica. Antes de alterar o plano, a app guarda uma cópia que pode ser revertida.
