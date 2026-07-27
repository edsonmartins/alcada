# Tarefas — 021 app móvel base

## Backend (este repo)
- [x] Migration: tabela `comando_movel` (idempotência por `(org_id, comando_id)`) + GuardaOrgId
- [x] Módulo `movel` (port/internal) — depende só das portas de triagem/autonomia/consulta/captura
- [x] `POST /v1/comandos`: lote, mapeia intenção → ação determinística (INV-10), na mesma transação (outbox)
- [x] Idempotência: reenvio devolve resultado gravado, não re-executa (C2)
- [x] IGNORADO para pendência inexistente/fechada; REGISTRAR nunca ignorado (C4)
- [x] CONSULTAR roteia para o pacote 020 (C5)
- [x] Atualizar `docs/API.md` com `/v1/comandos`
- [x] Testes: C1..C7 (despacha, idempotente, janela, ignorado, consulta, isolamento, registrar)
- [x] Verificar: JVM verde + native + RSS ≤120MB
- [x] Portas novas expostas: `Triagem`, `Autonomia`, `EscapeCaptura` (para o módulo movel)

## App (Flutter — repo separado `alcada-mobile`, ADR-0015)
- [x] Esqueleto Flutter offline-first; fila local persistida (sqflite)
- [x] Sincronizador idempotente (comandoId por comando); falha mantém pendente (INV-13)
- [x] Sessão por org_id/pessoa_id (piloto), limpeza de aspas + validação UUID; ganchos para OIDC
- [x] Lista da fila por pull + despacho de 1 toque (resolver) e registrar; sem push de "novo item"
- [x] Testes: enfileira offline → sincroniza sem duplicar; ERRO segue pendente (7 testes, analyze limpo)
- [x] Repassar com dono/nível (seletor de pessoa) — folha de repasse por nome (GET /v1/pessoas, avatar) + voz com diretório/apelidos
- [x] Sync em background + retry agendado — workmanager (WorkManager/BGTask): tarefa periódica (15min) com constraint de rede + backoff exponencial; isolate reconstrói fila+sessão+api e drena (INV-13). Foreground (timer/resume) mantido. iOS precisa de Info.plist/AppDelegate (a fazer)
- [x] Rodar em device/emulador — validado no Galaxy Tab (Android) ponta a ponta (voz, trajeto, UI)
