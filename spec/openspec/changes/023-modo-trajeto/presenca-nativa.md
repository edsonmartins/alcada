# Presença do trajeto — integrações nativas (023, fatia D)

A fatia D (CarPlay/Android Auto, áudio em background, Live Activity/notificação
persistente) é **majoritariamente código nativo de plataforma**, que exige um
Mac/dispositivo e entitlements — não é verificável no headless. Por isso ficou
**atrás de uma porta** (`lib/voz/presenca_trajeto.dart`), como `Stt` e a fonte de
movimento: o ciclo do trajeto já chama `iniciar()/atualizar()/encerrar()`; falta
plugar os motores nativos no `main` (hoje roda o `PresencaTrajetoStub`).

Lembrete (CLAUDE.md §8): **não é push de "novo item"** — é o indicador do trajeto em
curso, ligado pelo próprio gestor.

## O que já está pronto
- Porta `PresencaTrajeto` + `PresencaTrajetoStub` (testado).
- Fiação: `AssistenteVoz.iniciarTrajeto → iniciar`; condução → `atualizar("Item X de N")`;
  `pararTrajeto → encerrar`.

## O que falta (por plataforma) — plugar atrás da porta
1. **Android — foreground service + notificação contínua** (o mais acessível):
   `Service` com `startForeground` e uma notificação *ongoing* (não removível),
   canal dedicado, atualizada via `atualizar()`. Mantém o processo e o áudio vivos
   no trajeto. Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
   Via `MethodChannel`. **Requer build/dispositivo Android para verificar.**
2. **iOS — áudio em background + Live Activity**:
   `AVAudioSession` categoria `.playback` (background audio no Info.plist); ActivityKit
   (iOS 16+) numa *widget extension* para a Live Activity, atualizada por `atualizar()`.
   **Requer Xcode/dispositivo e uma app extension.**
3. **CarPlay** (iOS): entitlement da Apple (aprovação), `CPTemplateApplicationSceneDelegate`
   e templates (lista do item atual + ações). **Bloqueado por entitlement Apple.**
4. **Android Auto**: `androidx.car.app` (`CarAppService` + `Session` + templates),
   testado no DHU. **Requer setup nativo + Android Auto.**

## Recomendação de ordem
Android foreground service → iOS background audio + Live Activity → Android Auto →
CarPlay (último, depende de aprovação da Apple). Cada um entra sem tocar no resto:
implementa `PresencaTrajeto` e é injetado no `main` por plataforma.
