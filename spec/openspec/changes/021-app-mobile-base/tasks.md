# Tarefas — 021 app móvel base

## Backend (este repo)
- [ ] Migration: tabela `comando_movel` (idempotência por `(org_id, comando_id)`) + GuardaOrgId
- [ ] Módulo `movel` (port/internal) — depende só das portas de triagem/autonomia/consulta/captura
- [ ] `POST /v1/comandos`: lote, mapeia intenção → ação determinística (INV-10), na mesma transação (outbox)
- [ ] Idempotência: reenvio devolve resultado gravado, não re-executa (C2)
- [ ] IGNORADO para pendência inexistente/fechada; REGISTRAR nunca ignorado (C4)
- [ ] CONSULTAR roteia para o pacote 020 (C5)
- [ ] Atualizar `docs/API.md` com `/v1/comandos`
- [ ] Testes: C1..C7 (despacha, idempotente, janela, ignorado, consulta, isolamento, sem-push)
- [ ] Verificar: JVM verde + native + RSS ≤120MB

## App (Flutter — repo/target separado, ADR-0015)
- [ ] Esqueleto Flutter 3.27 offline-first; fila local persistida (Drift/SQLite)
- [ ] Worker de sync idempotente com retry exponencial
- [ ] Sessão por org_id/pessoa_id (piloto); ganchos para OIDC
- [ ] Lista da fila (leitura) + ações de despacho de 1 toque, sem push de "novo item"
- [ ] Teste offline: comando enfileirado sem rede sincroniza ao voltar, sem duplicar
